package com.netpulse.client.net;

/** Raised when the backend answers with a non-2xx status or an unparseable body. */
public class ApiException extends RuntimeException {

    private final int statusCode;

    public ApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public ApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
    }

    /** HTTP status, or {@code -1} when the request never reached the server. */
    public int getStatusCode() {
        return statusCode;
    }
}
