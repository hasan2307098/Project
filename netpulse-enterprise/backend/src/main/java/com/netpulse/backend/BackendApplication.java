package com.netpulse.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.netpulse.backend.config.AppConfig;
import com.netpulse.backend.config.DatabaseManager;
import com.netpulse.backend.controller.EndpointController;
import com.netpulse.backend.controller.MetricsController;
import com.netpulse.backend.dao.EndpointDao;
import com.netpulse.backend.dao.LatencyLogDao;
import com.netpulse.backend.dto.ApiResponse;
import com.netpulse.backend.exception.DataAccessException;
import com.netpulse.backend.exception.NotFoundException;
import com.netpulse.backend.exception.ValidationException;
import com.netpulse.backend.service.EndpointService;
import com.netpulse.backend.service.MetricsService;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Entry point: wires DAO -> Service -> Controller, installs the error mapping,
 * and starts the embedded HTTP server.
 */
public final class BackendApplication {

    private static final Logger LOG = LoggerFactory.getLogger(BackendApplication.class);

    public static void main(String[] args) {
        DatabaseManager.init();
        if (!DatabaseManager.healthCheck()) {
            LOG.error("Cannot reach the database at {}. Check NETPULSE_DB_URL / credentials and that schema.sql has been applied.",
                    AppConfig.dbUrl());
            System.exit(1);
        }

        // ---- dependency wiring (manual DI keeps the object graph obvious) ----
        EndpointDao endpointDao = new EndpointDao();
        LatencyLogDao latencyLogDao = new LatencyLogDao();
        EndpointService endpointService = new EndpointService(endpointDao);
        MetricsService metricsService = new MetricsService(latencyLogDao, endpointDao);

        Javalin app = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(buildObjectMapper()));
            config.showJavalinBanner = false;
        });

        new EndpointController(endpointService).register(app);
        new MetricsController(metricsService).register(app);

        app.get("/api/health", ctx -> ctx.json(ApiResponse.ok(
                Map.of("status", DatabaseManager.healthCheck() ? "UP" : "DEGRADED",
                       "service", "netpulse-backend"),
                "Service reachable")));

        installErrorHandling(app);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutting down NetPulse backend...");
            app.stop();
            DatabaseManager.shutdown();
        }, "netpulse-shutdown"));

        app.start(AppConfig.httpPort());
        LOG.info("NetPulse backend listening on http://localhost:{}", AppConfig.httpPort());
    }

    /** ISO-8601 timestamps in, ISO-8601 timestamps out - no epoch numbers. */
    private static ObjectMapper buildObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /** Maps every exception type onto its HTTP status while keeping one JSON envelope. */
    private static void installErrorHandling(Javalin app) {
        app.exception(ValidationException.class, (ex, ctx) ->
                ctx.status(400).json(ApiResponse.fail(ex.getMessage())));

        app.exception(NotFoundException.class, (ex, ctx) ->
                ctx.status(404).json(ApiResponse.fail(ex.getMessage())));

        app.exception(DataAccessException.class, (ex, ctx) -> {
            LOG.error("Database failure on {} {}", ctx.method(), ctx.path(), ex);
            ctx.status(500).json(ApiResponse.fail("Database error: " + ex.getMessage()));
        });

        app.exception(Exception.class, (ex, ctx) -> {
            LOG.error("Unhandled failure on {} {}", ctx.method(), ctx.path(), ex);
            ctx.status(500).json(ApiResponse.fail("Internal server error."));
        });

        app.error(404, ctx -> {
            if (ctx.result() == null || ctx.result().isBlank()) {
                ctx.json(ApiResponse.fail("No route matches " + ctx.method() + " " + ctx.path()));
            }
        });
    }

    private BackendApplication() {
    }
}
