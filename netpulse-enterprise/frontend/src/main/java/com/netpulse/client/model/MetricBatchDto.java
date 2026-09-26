package com.netpulse.client.model;

import java.util.List;

/** Request body wrapper for the batch ingest call. */
public class MetricBatchDto {

    private List<MetricSampleDto> samples;

    public MetricBatchDto() {
    }

    public MetricBatchDto(List<MetricSampleDto> samples) {
        this.samples = samples;
    }

    public List<MetricSampleDto> getSamples() { return samples; }
    public void setSamples(List<MetricSampleDto> samples) { this.samples = samples; }
}
