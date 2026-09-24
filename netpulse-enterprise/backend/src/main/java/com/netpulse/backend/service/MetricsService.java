package com.netpulse.backend.service;

import com.netpulse.backend.config.AppConfig;
import com.netpulse.backend.dao.EndpointDao;
import com.netpulse.backend.dao.LatencyLogDao;
import com.netpulse.backend.dto.MetricBatchRequest;
import com.netpulse.backend.dto.MetricSample;
import com.netpulse.backend.exception.NotFoundException;
import com.netpulse.backend.exception.ValidationException;
import com.netpulse.backend.model.LatencyLog;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Ingests batched probe results and serves chart history. */
public class MetricsService {

    private final LatencyLogDao latencyLogDao;
    private final EndpointDao endpointDao;

    public MetricsService(LatencyLogDao latencyLogDao, EndpointDao endpointDao) {
        this.latencyLogDao = latencyLogDao;
        this.endpointDao = endpointDao;
    }

    /**
     * Validates and persists one batch.
     *
     * <p>Foreign keys are checked in a single {@code IN (...)} lookup rather than
     * per row, and an unknown endpoint id is rejected with 400 instead of
     * surfacing as an opaque FK violation from the database.</p>
     *
     * @return number of samples written
     */
    public int ingestBatch(MetricBatchRequest request) {
        if (request == null || request.getSamples() == null) {
            throw new ValidationException("Field 'samples' is required.");
        }
        List<MetricSample> samples = request.getSamples();
        if (samples.isEmpty()) {
            throw new ValidationException("Field 'samples' must contain at least one entry.");
        }
        int max = AppConfig.maxBatchSize();
        if (samples.size() > max) {
            throw new ValidationException("A batch may contain at most " + max + " samples.");
        }

        Set<Long> referencedIds = new HashSet<>();
        for (MetricSample sample : samples) {
            if (sample.getEndpointId() == null) {
                throw new ValidationException("Every sample requires an 'endpointId'.");
            }
            if (sample.getReachable() == null) {
                throw new ValidationException("Every sample requires a 'reachable' flag.");
            }
            if (sample.getLatencyMs() != null && sample.getLatencyMs() < 0) {
                throw new ValidationException("Field 'latencyMs' must not be negative.");
            }
            referencedIds.add(sample.getEndpointId());
        }

        Set<Long> known = endpointDao.findExistingIds(referencedIds);
        for (Long id : referencedIds) {
            if (!known.contains(id)) {
                throw new ValidationException("Batch references unknown endpoint id " + id + ".");
            }
        }

        List<LatencyLog> rows = new ArrayList<>(samples.size());
        for (MetricSample sample : samples) {
            boolean reachable = Boolean.TRUE.equals(sample.getReachable());
            rows.add(new LatencyLog(
                    sample.getEndpointId(),
                    reachable ? sample.getLatencyMs() : null,   // unreachable -> no latency value
                    reachable,
                    sample.getStatusMessage(),
                    sample.getRecordedAt() == null ? OffsetDateTime.now() : sample.getRecordedAt()));
        }
        return latencyLogDao.insertBatch(rows);
    }

    public List<LatencyLog> history(long endpointId, int limit) {
        if (limit < 1) {
            throw new ValidationException("Query parameter 'limit' must be at least 1.");
        }
        int capped = Math.min(limit, AppConfig.maxHistoryLimit());
        if (endpointDao.findById(endpointId).isEmpty()) {
            throw new NotFoundException("No endpoint exists with id " + endpointId);
        }
        return latencyLogDao.findRecent(endpointId, capped);
    }
}
