package com.netpulse.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Request body for {@code POST /api/endpoints}. All validation happens in the service layer. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EndpointRequest {

    private String name;
    private String targetAddress;
    private Integer checkIntervalSec;
    private Integer timeoutMs;
    private Boolean active;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTargetAddress() { return targetAddress; }
    public void setTargetAddress(String targetAddress) { this.targetAddress = targetAddress; }

    public Integer getCheckIntervalSec() { return checkIntervalSec; }
    public void setCheckIntervalSec(Integer checkIntervalSec) { this.checkIntervalSec = checkIntervalSec; }

    public Integer getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(Integer timeoutMs) { this.timeoutMs = timeoutMs; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
