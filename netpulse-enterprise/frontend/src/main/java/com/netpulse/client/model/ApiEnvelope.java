package com.netpulse.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Client-side mirror of the backend's {@code {success, data, message}} envelope. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiEnvelope<T> {

    private boolean success;
    private T data;
    private String message;

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public T getData() { return data; }
    public void setData(T data) { this.data = data; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
