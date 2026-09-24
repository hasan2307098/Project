package com.netpulse.backend.exception;

/** Wraps a checked {@link java.sql.SQLException} so controllers stay clean; mapped to HTTP 500. */
public class DataAccessException extends RuntimeException {
    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
