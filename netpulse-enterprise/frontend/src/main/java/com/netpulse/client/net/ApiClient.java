package com.netpulse.client.net;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.netpulse.client.model.ApiEnvelope;
import com.netpulse.client.model.EndpointDto;
import com.netpulse.client.model.LatencyLogDto;
import com.netpulse.client.model.MetricBatchDto;
import com.netpulse.client.model.MetricSampleDto;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Asynchronous REST client for the NetPulse backend.
 *
 * <p>Built on the JDK 11+ {@link HttpClient}. Every call returns a
 * {@link CompletableFuture}, so the caller (the JavaFX controller) never blocks
 * the FX Application Thread waiting on a socket. The client owns a small daemon
 * executor, which keeps the JVM from being held open by in-flight requests when
 * the window closes.</p>
 */
public class ApiClient implements AutoCloseable {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(6);

    private final String baseUrl;
    private final HttpClient http;
    private final ExecutorService executor;
    private final ObjectMapper mapper;

    public ApiClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;

        AtomicInteger counter = new AtomicInteger();
        this.executor = Executors.newFixedThreadPool(2, runnable -> {
            Thread t = new Thread(runnable, "netpulse-api-" + counter.incrementAndGet());
            t.setDaemon(true);      // never block JVM exit
            return t;
        });

        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(CONNECT_TIMEOUT)
                .executor(executor)
                .build();

        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    // -----------------------------------------------------------------
    // Endpoint resource
    // -----------------------------------------------------------------

    public CompletableFuture<List<EndpointDto>> listEndpoints() {
        HttpRequest request = get("/api/endpoints");
        return send(request, new TypeReference<ApiEnvelope<List<EndpointDto>>>() { });
    }

    public CompletableFuture<EndpointDto> createEndpoint(EndpointDto endpoint) {
        HttpRequest request = jsonRequest("/api/endpoints", "POST", endpoint);
        return send(request, new TypeReference<ApiEnvelope<EndpointDto>>() { });
    }

    public CompletableFuture<Void> deleteEndpoint(long id) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/endpoints/" + id))
                .timeout(REQUEST_TIMEOUT)
                .DELETE()
                .build();
        return send(request, new TypeReference<ApiEnvelope<Object>>() { }).thenApply(ignored -> null);
    }

    // -----------------------------------------------------------------
    // Metrics resource
    // -----------------------------------------------------------------

    /** Ships one drained batch of probe results; resolves to the inserted row count. */
    public CompletableFuture<Integer> postMetricBatch(List<MetricSampleDto> samples) {
        HttpRequest request = jsonRequest("/api/metrics/batch", "POST", new MetricBatchDto(samples));
        return send(request, new TypeReference<ApiEnvelope<Map<String, Integer>>>() { })
                .thenApply(data -> data == null ? 0 : data.getOrDefault("inserted", 0));
    }

    public CompletableFuture<List<LatencyLogDto>> history(long endpointId, int limit) {
        HttpRequest request = get("/api/metrics/" + endpointId + "/history?limit=" + limit);
        return send(request, new TypeReference<ApiEnvelope<List<LatencyLogDto>>>() { });
    }

    public CompletableFuture<Boolean> ping() {
        return send(get("/api/health"), new TypeReference<ApiEnvelope<Map<String, String>>>() { })
                .thenApply(data -> data != null)
                .exceptionally(ex -> false);
    }

    // -----------------------------------------------------------------
    // plumbing
    // -----------------------------------------------------------------

    private HttpRequest get(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();
    }

    private HttpRequest jsonRequest(String path, String method, Object body) {
        try {
            String json = mapper.writeValueAsString(body);
            return HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(json))
                    .build();
        } catch (Exception ex) {
            throw new ApiException("Unable to serialize request body", ex);
        }
    }

    /**
     * Sends asynchronously and unwraps the {@code {success, data, message}} envelope.
     * A non-2xx status is converted into an {@link ApiException} carrying the
     * server's own {@code message}, so the UI can show something meaningful
     * instead of a bare status code.
     */
    private <T> CompletableFuture<T> send(HttpRequest request, TypeReference<ApiEnvelope<T>> type) {
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    String body = response.body();
                    ApiEnvelope<T> envelope = null;
                    try {
                        if (body != null && !body.isBlank()) {
                            envelope = mapper.readValue(body, type);
                        }
                    } catch (Exception parseFailure) {
                        if (response.statusCode() / 100 == 2) {
                            throw new ApiException("Malformed JSON from server: " + parseFailure.getMessage(),
                                    parseFailure);
                        }
                    }
                    if (response.statusCode() / 100 != 2) {
                        String message = envelope != null && envelope.getMessage() != null
                                ? envelope.getMessage()
                                : "HTTP " + response.statusCode();
                        throw new ApiException(response.statusCode(), message);
                    }
                    return envelope == null ? null : envelope.getData();
                });
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
