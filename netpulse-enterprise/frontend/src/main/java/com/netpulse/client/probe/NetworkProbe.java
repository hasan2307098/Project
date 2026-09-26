package com.netpulse.client.probe;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Performs one reachability measurement against a target.
 *
 * <p>Two probe strategies, chosen from the address shape:</p>
 * <ul>
 *   <li><b>http:// or https://</b> - an HTTP {@code HEAD} request. HEAD is used
 *       because it returns headers only, so the measurement reflects round-trip
 *       time rather than payload size. Servers that reject HEAD (405/501) get one
 *       automatic GET retry.</li>
 *   <li><b>host[:port]</b> - a raw TCP connect, timing the three-way handshake.
 *       This works for non-HTTP services such as DNS (53) or a database port.</li>
 * </ul>
 *
 * <p>This class is stateless and thread-safe; a single instance is shared by the
 * whole worker pool.</p>
 */
public class NetworkProbe {

    private static final int DEFAULT_TCP_PORT = 80;

    private final HttpClient httpClient;
    private final ExecutorService httpExecutor;

    public NetworkProbe() {
        // A dedicated daemon pool, deliberately separate from the probe worker
        // pool: sharing one pool would let saturated workers starve the HTTP
        // client's own internal tasks.
        AtomicInteger counter = new AtomicInteger();
        this.httpExecutor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "netpulse-http-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .executor(httpExecutor)
                .build();
    }

    /** Releases the internal HTTP executor; called during application shutdown. */
    public void shutdown() {
        httpExecutor.shutdownNow();
    }

    /** Never throws - every failure mode is reported as an unreachable result. */
    public ProbeResult probe(long endpointId, String name, String address, int timeoutMs) {
        try {
            String lower = address.toLowerCase();
            if (lower.startsWith("http://") || lower.startsWith("https://")) {
                return httpProbe(endpointId, name, address, timeoutMs);
            }
            return tcpProbe(endpointId, name, address, timeoutMs);
        } catch (Exception unexpected) {
            return ProbeResult.unreachable(endpointId, name,
                    "Probe error: " + unexpected.getClass().getSimpleName());
        }
    }

    // -----------------------------------------------------------------
    // HTTP HEAD strategy
    // -----------------------------------------------------------------
    private ProbeResult httpProbe(long id, String name, String url, int timeoutMs) {
        long startNanos = System.nanoTime();
        try {
            HttpRequest head = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("User-Agent", "NetPulse/1.0")
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<Void> response = httpClient.send(head, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();

            // Some servers refuse HEAD; fall back once to a GET before declaring failure.
            if (status == 405 || status == 501) {
                return httpGetFallback(id, name, url, timeoutMs);
            }

            int elapsed = elapsedMillis(startNanos);
            if (status >= 500) {
                return ProbeResult.unreachable(id, name, "Server error HTTP " + status);
            }
            return ProbeResult.reachable(id, name, elapsed, "HTTP " + status);

        } catch (HttpTimeoutException ex) {
            return ProbeResult.unreachable(id, name, "Timeout after " + timeoutMs + " ms");
        } catch (ConnectException ex) {
            return ProbeResult.unreachable(id, name, "Connection refused");
        } catch (IOException ex) {
            Throwable root = rootCause(ex);
            if (root instanceof UnknownHostException) {
                return ProbeResult.unreachable(id, name, "DNS lookup failed");
            }
            return ProbeResult.unreachable(id, name, "Network error: " + shortMessage(root));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return ProbeResult.unreachable(id, name, "Probe interrupted");
        } catch (IllegalArgumentException ex) {
            return ProbeResult.unreachable(id, name, "Malformed URL");
        }
    }

    private ProbeResult httpGetFallback(long id, String name, String url, int timeoutMs) {
        long startNanos = System.nanoTime();
        try {
            HttpRequest get = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("User-Agent", "NetPulse/1.0")
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(get, HttpResponse.BodyHandlers.discarding());
            int elapsed = elapsedMillis(startNanos);
            int status = response.statusCode();
            if (status >= 500) {
                return ProbeResult.unreachable(id, name, "Server error HTTP " + status);
            }
            return ProbeResult.reachable(id, name, elapsed, "HTTP " + status + " (GET)");
        } catch (HttpTimeoutException ex) {
            return ProbeResult.unreachable(id, name, "Timeout after " + timeoutMs + " ms");
        } catch (IOException ex) {
            return ProbeResult.unreachable(id, name, "Network error: " + shortMessage(rootCause(ex)));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return ProbeResult.unreachable(id, name, "Probe interrupted");
        }
    }

    // -----------------------------------------------------------------
    // Raw TCP strategy
    // -----------------------------------------------------------------
    private ProbeResult tcpProbe(long id, String name, String address, int timeoutMs) {
        String host = address;
        int port = DEFAULT_TCP_PORT;

        int colon = address.lastIndexOf(':');
        if (colon > 0 && colon < address.length() - 1) {
            try {
                port = Integer.parseInt(address.substring(colon + 1));
                host = address.substring(0, colon);
            } catch (NumberFormatException ignored) {
                // not a port suffix - treat the whole string as a hostname
            }
        }

        long startNanos = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.setSoTimeout(timeoutMs);
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            int elapsed = elapsedMillis(startNanos);
            return ProbeResult.reachable(id, name, elapsed, "TCP " + port + " open");
        } catch (SocketTimeoutException ex) {
            return ProbeResult.unreachable(id, name, "Timeout after " + timeoutMs + " ms");
        } catch (UnknownHostException ex) {
            return ProbeResult.unreachable(id, name, "DNS lookup failed");
        } catch (ConnectException ex) {
            return ProbeResult.unreachable(id, name, "Connection refused on port " + port);
        } catch (IOException ex) {
            return ProbeResult.unreachable(id, name, "Socket error: " + shortMessage(ex));
        } catch (IllegalArgumentException ex) {
            return ProbeResult.unreachable(id, name, "Invalid host or port");
        }
    }

    // -----------------------------------------------------------------
    private int elapsedMillis(long startNanos) {
        return (int) Math.max(0, (System.nanoTime() - startNanos) / 1_000_000L);
    }

    private Throwable rootCause(Throwable t) {
        Throwable current = t;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private String shortMessage(Throwable t) {
        String msg = t.getMessage();
        if (msg == null || msg.isBlank()) {
            return t.getClass().getSimpleName();
        }
        return msg.length() > 80 ? msg.substring(0, 80) : msg;
    }
}
