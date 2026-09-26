package com.netpulse.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Wire format for an endpoint, matching the backend model field-for-field. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EndpointDto {

    private Long id;
    private String name;
    private String targetAddress;
    private int checkIntervalSec = 5;
    private int timeoutMs = 1500;
    private boolean active = true;

    public EndpointDto() {
    }

    public EndpointDto(String name, String targetAddress, int checkIntervalSec, int timeoutMs) {
        this.name = name;
        this.targetAddress = targetAddress;
        this.checkIntervalSec = checkIntervalSec;
        this.timeoutMs = timeoutMs;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTargetAddress() { return targetAddress; }
    public void setTargetAddress(String targetAddress) { this.targetAddress = targetAddress; }

    public int getCheckIntervalSec() { return checkIntervalSec; }
    public void setCheckIntervalSec(int checkIntervalSec) { this.checkIntervalSec = checkIntervalSec; }

    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
