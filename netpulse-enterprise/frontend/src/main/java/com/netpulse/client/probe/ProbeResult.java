package com.netpulse.client.probe;

import java.time.OffsetDateTime;

/**
 * Immutable outcome of a single probe.
 *
 * <p>Immutability is deliberate: this object is created on a worker thread,
 * handed to the UI thread through {@code Platform.runLater} and simultaneously
 * queued for the backend uploader. With no mutable state there is nothing to
 * synchronize and no chance of a half-updated row being rendered.</p>
 */
public final class ProbeResult {

    private final long endpointId;
    private final String endpointName;
    private final Integer latencyMs;      // null when unreachable
    private final boolean reachable;
    private final String statusMessage;
    private final OffsetDateTime recordedAt;

    public ProbeResult(long endpointId, String endpointName, Integer latencyMs,
                       boolean reachable, String statusMessage, OffsetDateTime recordedAt) {
        this.endpointId = endpointId;
        this.endpointName = endpointName;
        this.latencyMs = latencyMs;
        this.reachable = reachable;
        this.statusMessage = statusMessage;
        this.recordedAt = recordedAt;
    }

    public static ProbeResult reachable(long id, String name, int latencyMs, String message) {
        return new ProbeResult(id, name, latencyMs, true, message, OffsetDateTime.now());
    }

    public static ProbeResult unreachable(long id, String name, String message) {
        return new ProbeResult(id, name, null, false, message, OffsetDateTime.now());
    }

    public long getEndpointId() { return endpointId; }
    public String getEndpointName() { return endpointName; }
    public Integer getLatencyMs() { return latencyMs; }
    public boolean isReachable() { return reachable; }
    public String getStatusMessage() { return statusMessage; }
    public OffsetDateTime getRecordedAt() { return recordedAt; }

    @Override
    public String toString() {
        return endpointName + " -> " + (reachable ? latencyMs + " ms" : "DOWN") + " (" + statusMessage + ")";
    }
}
