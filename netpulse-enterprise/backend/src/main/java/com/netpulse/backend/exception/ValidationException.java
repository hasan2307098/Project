package com.netpulse.backend.exception;

/** Thrown by the service layer for malformed input; mapped to HTTP 400. */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}
