package com.netpulse.backend.controller;

import com.netpulse.backend.dto.ApiResponse;
import com.netpulse.backend.dto.MetricBatchRequest;
import com.netpulse.backend.exception.ValidationException;
import com.netpulse.backend.model.LatencyLog;
import com.netpulse.backend.service.MetricsService;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.List;
import java.util.Map;

/**
 * REST surface for latency telemetry.
 *
 * <pre>
 * POST /api/metrics/batch          -> 201 ingested | 400 validation
 * GET  /api/metrics/{id}/history   -> 200 samples  | 404 unknown id
 * </pre>
 */
public class MetricsController {

    private static final int DEFAULT_HISTORY_LIMIT = 20;

    private final MetricsService service;

    public MetricsController(MetricsService service) {
        this.service = service;
    }

    public void register(Javalin app) {
        app.post("/api/metrics/batch", this::ingest);
        app.get("/api/metrics/{id}/history", this::history);
    }

    private void ingest(Context ctx) {
        MetricBatchRequest request;
        try {
            request = ctx.bodyAsClass(MetricBatchRequest.class);
        } catch (Exception ex) {
            throw new ValidationException("Request body is not a valid metric batch.");
        }
        int written = service.ingestBatch(request);
        ctx.status(201).json(ApiResponse.ok(
                Map.of("inserted", written),
                written + " sample(s) ingested"));
    }

    private void history(Context ctx) {
        long id = ControllerSupport.pathId(ctx);
        int limit = ControllerSupport.intQueryParam(ctx, "limit", DEFAULT_HISTORY_LIMIT);
        List<LatencyLog> samples = service.history(id, limit);
        ctx.status(200).json(ApiResponse.ok(samples, samples.size() + " sample(s) returned"));
    }
}
