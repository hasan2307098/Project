package com.netpulse.client.probe;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Scheduler-side bookkeeping for one target.
 *
 * <p>{@code inFlight} is the anti-pile-up guard: if a probe is still running
 * when its next slot comes round (a hung TCP connect, say), the tick simply
 * skips it rather than queuing a second task. Without this a 10 s outage on a
 * 1 s interval would spawn ten stacked tasks per target and starve the pool.</p>
 */
final class MonitoredTarget {

    private final long id;
    private final String name;
    private final String address;
    private final int timeoutMs;
    private final long intervalMillis;
    private final AtomicBoolean inFlight = new AtomicBoolean(false);

    private volatile long nextDueAt;

    MonitoredTarget(long id, String name, String address, int timeoutMs, int intervalSec) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.timeoutMs = timeoutMs;
        this.intervalMillis = Math.max(1, intervalSec) * 1000L;
        this.nextDueAt = System.currentTimeMillis();   // probe immediately on start
    }

    long id() { return id; }
    String name() { return name; }
    String address() { return address; }
    int timeoutMs() { return timeoutMs; }

    boolean isDue(long now) { return now >= nextDueAt; }

    void scheduleNext(long now) { this.nextDueAt = now + intervalMillis; }

    boolean tryAcquire() { return inFlight.compareAndSet(false, true); }

    void release() { inFlight.set(false); }
}
