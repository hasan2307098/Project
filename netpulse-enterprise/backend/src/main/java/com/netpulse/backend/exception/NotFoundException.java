package com.netpulse.backend.exception;

/** Thrown when a referenced row does not exist; mapped to HTTP 404. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
