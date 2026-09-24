package com.netpulse.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Request body for {@code POST /api/metrics/batch}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MetricBatchRequest {

    private List<MetricSample> samples;

    public List<MetricSample> getSamples() { return samples; }
    public void setSamples(List<MetricSample> samples) { this.samples = samples; }
}
