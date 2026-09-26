package com.netpulse.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;

/** One probe outcome as sent to {@code POST /api/metrics/batch}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MetricSampleDto {

    private Long endpointId;
    private Integer latencyMs;
    private Boolean reachable;
    private String statusMessage;
    private OffsetDateTime recordedAt;

    public MetricSampleDto() {
    }

    public MetricSampleDto(Long endpointId, Integer latencyMs, Boolean reachable,
                           String statusMessage, OffsetDateTime recordedAt) {
        this.endpointId = endpointId;
        this.latencyMs = latencyMs;
        this.reachable = reachable;
        this.statusMessage = statusMessage;
        this.recordedAt = recordedAt;
    }

    public Long getEndpointId() { return endpointId; }
    public void setEndpointId(Long endpointId) { this.endpointId = endpointId; }

    public Integer getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Integer latencyMs) { this.latencyMs = latencyMs; }

    public Boolean getReachable() { return reachable; }
    public void setReachable(Boolean reachable) { this.reachable = reachable; }

    public String getStatusMessage() { return statusMessage; }
    public void setStatusMessage(String statusMessage) { this.statusMessage = statusMessage; }

    public OffsetDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(OffsetDateTime recordedAt) { this.recordedAt = recordedAt; }
}
