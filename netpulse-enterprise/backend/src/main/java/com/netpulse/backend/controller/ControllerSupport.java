package com.netpulse.backend.controller;

import com.netpulse.backend.exception.ValidationException;
import io.javalin.http.Context;

/**
 * Small parsing helpers shared by the controllers.
 *
 * <p>They exist so that a malformed path or query parameter produces the same
 * {@code {success,data,message}} envelope as every other error, instead of
 * Javalin's built-in problem payload.</p>
 */
final class ControllerSupport {

    private ControllerSupport() {
    }

    static long pathId(Context ctx) {
        String raw = ctx.pathParam("id");
        try {
            long id = Long.parseLong(raw);
            if (id <= 0) {
                throw new ValidationException("Path parameter 'id' must be positive.");
            }
            return id;
        } catch (NumberFormatException ex) {
            throw new ValidationException("Path parameter 'id' must be numeric, got '" + raw + "'.");
        }
    }

    static int intQueryParam(Context ctx, String name, int fallback) {
        String raw = ctx.queryParam(name);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            throw new ValidationException("Query parameter '" + name + "' must be an integer.");
        }
    }
}
