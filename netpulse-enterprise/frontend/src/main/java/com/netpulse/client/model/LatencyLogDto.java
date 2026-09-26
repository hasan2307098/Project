package com.netpulse.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;

/** One historical sample returned by {@code GET /api/metrics/{id}/history}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LatencyLogDto {

    private Long id;
    private Long endpointId;
    private Integer latencyMs;
    private boolean reachable;
    private String statusMessage;
    private OffsetDateTime recordedAt;

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
