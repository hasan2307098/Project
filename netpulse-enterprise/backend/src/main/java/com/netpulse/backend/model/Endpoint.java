package com.netpulse.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;

/** Domain model mirroring one row of the {@code endpoints} table. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Endpoint {

    private Long id;
    private String name;
    private String targetAddress;
    private int checkIntervalSec = 5;
    private int timeoutMs = 1500;
    private boolean active = true;
    private OffsetDateTime createdAt;

    public Endpoint() {
    }

    public Endpoint(Long id, String name, String targetAddress, int checkIntervalSec,
                    int timeoutMs, boolean active, OffsetDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.targetAddress = targetAddress;
        this.checkIntervalSec = checkIntervalSec;
        this.timeoutMs = timeoutMs;
        this.active = active;
        this.createdAt = createdAt;
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

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "Endpoint{id=" + id + ", name='" + name + "', target='" + targetAddress + "'}";
    }
}
