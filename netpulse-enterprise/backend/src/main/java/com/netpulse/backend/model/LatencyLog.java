package com.netpulse.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;

/** Domain model mirroring one row of the {@code latency_logs} table. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LatencyLog {

    private Long id;
    private Long endpointId;
    /** {@code null} when the target was unreachable - no meaningful latency exists. */
    private Integer latencyMs;
    private boolean reachable;
    private String statusMessage;
    private OffsetDateTime recordedAt;

    public LatencyLog() {
    }

    public LatencyLog(Long endpointId, Integer latencyMs, boolean reachable,
                      String statusMessage, OffsetDateTime recordedAt) {
        this.endpointId = endpointId;
        this.latencyMs = latencyMs;
        this.reachable = reachable;
        this.statusMessage = statusMessage;
        this.recordedAt = recordedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getEndpointId() { return endpointId; }
    public void setEndpointId(Long endpointId) { this.endpointId = endpointId; }

    public Integer getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Integer latencyMs) { this.latencyMs = latencyMs; }

    public boolean isReachable() { return reachable; }
    public void setReachable(boolean reachable) { this.reachable = reachable; }

    public String getStatusMessage() { return statusMessage; }
    public void setStatusMessage(String statusMessage) { this.statusMessage = statusMessage; }

    public OffsetDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(OffsetDateTime recordedAt) { this.recordedAt = recordedAt; }
}
