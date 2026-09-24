package com.netpulse.backend.controller;

import com.netpulse.backend.dto.ApiResponse;
import com.netpulse.backend.dto.EndpointRequest;
import com.netpulse.backend.exception.ValidationException;
import com.netpulse.backend.model.Endpoint;
import com.netpulse.backend.service.EndpointService;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.List;

/**
 * REST surface for monitored targets.
 *
 * <pre>
 * GET    /api/endpoints       -> 200 list
 * POST   /api/endpoints       -> 201 created | 400 validation
 * DELETE /api/endpoints/{id}  -> 200 deleted | 404 unknown id
 * </pre>
 */
public class EndpointController {

    private final EndpointService service;

    public EndpointController(EndpointService service) {
        this.service = service;
    }

    public void register(Javalin app) {
        app.get("/api/endpoints", this::list);
        app.post("/api/endpoints", this::create);
        app.delete("/api/endpoints/{id}", this::delete);
    }

    private void list(Context ctx) {
        List<Endpoint> endpoints = service.listAll();
        ctx.status(200).json(ApiResponse.ok(endpoints, endpoints.size() + " endpoint(s) returned"));
    }

    private void create(Context ctx) {
        EndpointRequest request = readBody(ctx);
        Endpoint created = service.create(request);
        ctx.status(201).json(ApiResponse.ok(created, "Endpoint created"));
    }

    private void delete(Context ctx) {
        long id = ControllerSupport.pathId(ctx);
        service.delete(id);
        ctx.status(200).json(ApiResponse.ok(null, "Endpoint " + id + " deleted"));
    }

    private EndpointRequest readBody(Context ctx) {
        try {
            return ctx.bodyAsClass(EndpointRequest.class);
        } catch (Exception ex) {
            throw new ValidationException("Request body is not valid JSON for an endpoint.");
        }
    }
}
