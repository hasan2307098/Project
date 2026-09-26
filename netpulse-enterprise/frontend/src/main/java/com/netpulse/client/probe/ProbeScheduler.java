package com.netpulse.client.probe;

import com.netpulse.client.model.MetricSampleDto;
import com.netpulse.client.net.ApiClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * The concurrency engine of the desktop client.
 *
 * <h2>Thread model</h2>
 * <pre>
 *   scheduler pool (1 daemon thread)
 *       |- tick every 500 ms: which targets are due?
 *       |- flush every 3 s   : drain queue -> ApiClient (async)
 *       v
 *   worker pool (N daemon threads)  -- runs blocking HTTP HEAD / TCP connects
 *       |- result -> resultListener (background thread, controller re-dispatches
 *       |            it onto the FX thread with Platform.runLater)
 *       v
 *   LinkedBlockingQueue&lt;ProbeResult&gt;  -- thread-safe hand-off to the uploader
 * </pre>
 *
 * <h2>Why it cannot freeze the UI</h2>
 * Nothing here touches a JavaFX node. Probes block on sockets, never on the FX
 * thread; results cross the boundary as immutable objects. Task pile-up is
 * prevented by a per-target in-flight flag plus the rule that
 * {@code timeoutMs <= checkIntervalSec * 1000} (enforced by the backend's
 * validation), so a target can have at most one outstanding probe.
 *
 * <h2>Backend outages</h2>
 * Probing is decoupled from uploading. If the API is down, flushes fail, samples
 * are returned to the bounded queue and the dashboard keeps updating live. The
 * queue bound stops an extended outage from turning into an OOM - the oldest
 * surplus is dropped and counted instead.
 */
public class ProbeScheduler {

    private static final long TICK_MILLIS = 500;
    private static final long FLUSH_SECONDS = 3;
    private static final int MAX_FLUSH_BATCH = 200;
    private static final int QUEUE_CAPACITY = 5_000;

    private final ApiClient apiClient;
    private final NetworkProbe probe;
    private final Consumer<ProbeResult> resultListener;
    private final Consumer<String> statusListener;

    private final ScheduledExecutorService scheduler;
    private final ExecutorService workers;

    /** Thread-safe staging area between the probe workers and the uploader. */
    private final BlockingQueue<ProbeResult> pendingUploads = new LinkedBlockingQueue<>(QUEUE_CAPACITY);

    private final Map<Long, MonitoredTarget> targets = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong probesCompleted = new AtomicLong();
    private final AtomicLong samplesUploaded = new AtomicLong();
    private final AtomicLong samplesDropped = new AtomicLong();

    private ScheduledFuture<?> tickTask;
    private ScheduledFuture<?> flushTask;

    public ProbeScheduler(ApiClient apiClient,
                          int workerThreads,
                          Consumer<ProbeResult> resultListener,
                          Consumer<String> statusListener) {
        this.apiClient = apiClient;
        this.resultListener = resultListener;
        this.statusListener = statusListener;

        this.scheduler = Executors.newScheduledThreadPool(1, daemonFactory("netpulse-sched"));
        this.workers = Executors.newFixedThreadPool(Math.max(2, workerThreads), daemonFactory("netpulse-probe"));
        this.probe = new NetworkProbe();
    }

    // -----------------------------------------------------------------
    // target registry (safe to call while running)
    // -----------------------------------------------------------------

    public void registerTarget(long id, String name, String address, int timeoutMs, int intervalSec) {
        targets.put(id, new MonitoredTarget(id, name, address, timeoutMs, intervalSec));
    }

    public void unregisterTarget(long id) {
        targets.remove(id);
    }

    public void clearTargets() {
        targets.clear();
    }

    public boolean isRunning() {
        return running.get();
    }

    // -----------------------------------------------------------------
    // lifecycle
    // -----------------------------------------------------------------

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;         // already monitoring
        }
        long now = System.currentTimeMillis();
        targets.values().forEach(t -> t.scheduleNext(now - 1));   // fire on the first tick

        tickTask = scheduler.scheduleAtFixedRate(
                this::tick, 0, TICK_MILLIS, TimeUnit.MILLISECONDS);
        flushTask = scheduler.scheduleWithFixedDelay(
                this::flush, FLUSH_SECONDS, FLUSH_SECONDS, TimeUnit.SECONDS);

        notifyStatus("Monitoring started (" + targets.size() + " target(s))");
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        cancel(tickTask);
        cancel(flushTask);
        flush();            // don't lose what was already measured
        notifyStatus("Monitoring stopped");
    }

    /**
     * Terminal shutdown, called from the window's close handler.
     * Stops accepting work, drains one last batch and waits briefly for the
     * pools so no probe thread outlives the UI.
     */
    public void shutdown() {
        running.set(false);
        cancel(tickTask);
        cancel(flushTask);

        flushBlocking();

        scheduler.shutdownNow();
        probe.shutdown();
        workers.shutdown();
        try {
            if (!workers.awaitTermination(2, TimeUnit.SECONDS)) {
                workers.shutdownNow();      // interrupt sockets still blocked on connect
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            workers.shutdownNow();
        }
    }

    // -----------------------------------------------------------------
    // scheduling tick
    // -----------------------------------------------------------------

    /** Runs on the scheduler thread; dispatches only, never blocks on I/O. */
    private void tick() {
        if (!running.get()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (MonitoredTarget target : targets.values()) {
            if (!target.isDue(now)) {
                continue;
            }
            if (!target.tryAcquire()) {
                continue;   // previous probe still in flight - skip this slot
            }
            target.scheduleNext(now);
            try {
                workers.submit(() -> runProbe(target));
            } catch (RejectedExecutionException ex) {
                target.release();           // pool shutting down
            }
        }
    }

    /** Runs on a worker thread. */
    private void runProbe(MonitoredTarget target) {
        try {
            ProbeResult result = probe.probe(
                    target.id(), target.name(), target.address(), target.timeoutMs());

            probesCompleted.incrementAndGet();

            if (!pendingUploads.offer(result)) {
                pendingUploads.poll();              // bounded queue: shed the oldest sample
                pendingUploads.offer(result);
                samplesDropped.incrementAndGet();
            }
            if (resultListener != null) {
                resultListener.accept(result);      // controller marshals onto the FX thread
            }
        } finally {
            target.release();
        }
    }

    // -----------------------------------------------------------------
    // batched upload
    // -----------------------------------------------------------------

    /** Drains up to {@link #MAX_FLUSH_BATCH} samples and ships them asynchronously. */
    private void flush() {
        List<ProbeResult> batch = new ArrayList<>(MAX_FLUSH_BATCH);
        pendingUploads.drainTo(batch, MAX_FLUSH_BATCH);
        if (batch.isEmpty()) {
            return;
        }
        List<MetricSampleDto> payload = toPayload(batch);

        apiClient.postMetricBatch(payload)
                .thenAccept(inserted -> {
                    samplesUploaded.addAndGet(inserted);
                    notifyStatus("Synced " + inserted + " sample(s) to backend");
                })
                .exceptionally(ex -> {
                    requeue(batch);
                    notifyStatus("Backend sync failed (" + rootMessage(ex) + ") - buffering locally");
                    return null;
                });
    }

    /** Best-effort synchronous flush used during shutdown. */
    private void flushBlocking() {
        List<ProbeResult> batch = new ArrayList<>();
        pendingUploads.drainTo(batch, MAX_FLUSH_BATCH);
        if (batch.isEmpty()) {
            return;
        }
        try {
            apiClient.postMetricBatch(toPayload(batch)).get(2, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // The app is closing; an unsent final batch is acceptable.
        }
    }

    private List<MetricSampleDto> toPayload(List<ProbeResult> batch) {
        List<MetricSampleDto> payload = new ArrayList<>(batch.size());
        for (ProbeResult r : batch) {
            payload.add(new MetricSampleDto(
                    r.getEndpointId(), r.getLatencyMs(), r.isReachable(),
                    r.getStatusMessage(), r.getRecordedAt()));
        }
        return payload;
    }

    /** Puts a failed batch back at the tail of the queue, dropping any overflow. */
    private void requeue(List<ProbeResult> batch) {
        for (ProbeResult result : batch) {
            if (!pendingUploads.offer(result)) {
                samplesDropped.incrementAndGet();
            }
        }
    }

    // -----------------------------------------------------------------
    // telemetry for the status bar
    // -----------------------------------------------------------------

    public long getProbesCompleted() { return probesCompleted.get(); }
    public long getSamplesUploaded() { return samplesUploaded.get(); }
    public long getSamplesDropped() { return samplesDropped.get(); }
    public int getPendingUploadCount() { return pendingUploads.size(); }

    private void notifyStatus(String message) {
        if (statusListener != null) {
            statusListener.accept(message);
        }
    }

    private void cancel(ScheduledFuture<?> task) {
        if (task != null) {
            task.cancel(false);
        }
    }

    private String rootMessage(Throwable t) {
        Throwable current = t;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String msg = current.getMessage();
        return msg == null ? current.getClass().getSimpleName() : msg;
    }

    /**
     * Daemon threads only: the JVM must be able to exit the moment the last
     * window closes, even if a socket read is still parked.
     */
    private static ThreadFactory daemonFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);   // UI rendering wins ties
            return thread;
        };
    }
}
