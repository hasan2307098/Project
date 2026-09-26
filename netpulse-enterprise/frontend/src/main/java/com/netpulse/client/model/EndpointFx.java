package com.netpulse.client.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Observable view-model for one row of the dashboard's {@code TableView}.
 *
 * <p>These JavaFX properties are bound directly to table cells, which means
 * every mutation must happen on the FX Application Thread. The probe workers
 * never touch this class - they emit an immutable
 * {@link com.netpulse.client.probe.ProbeResult} and the controller applies it
 * inside {@code Platform.runLater(...)}.</p>
 */
public class EndpointFx {

    /** Sentinel used when no probe has completed yet. */
    public static final int NO_LATENCY = -1;

    private final LongProperty id = new SimpleLongProperty(this, "id");
    private final StringProperty name = new SimpleStringProperty(this, "name");
    private final StringProperty targetAddress = new SimpleStringProperty(this, "targetAddress");
    private final IntegerProperty checkIntervalSec = new SimpleIntegerProperty(this, "checkIntervalSec", 5);
    private final IntegerProperty timeoutMs = new SimpleIntegerProperty(this, "timeoutMs", 1500);
    private final IntegerProperty latencyMs = new SimpleIntegerProperty(this, "latencyMs", NO_LATENCY);
    private final StringProperty status = new SimpleStringProperty(this, "status", "PENDING");
    private final StringProperty statusMessage = new SimpleStringProperty(this, "statusMessage", "Awaiting first probe");
    private final BooleanProperty monitored = new SimpleBooleanProperty(this, "monitored", true);

    public EndpointFx() {
    }

    public static EndpointFx from(EndpointDto dto) {
        EndpointFx fx = new EndpointFx();
        fx.setId(dto.getId() == null ? 0L : dto.getId());
        fx.setName(dto.getName());
        fx.setTargetAddress(dto.getTargetAddress());
        fx.setCheckIntervalSec(dto.getCheckIntervalSec());
        fx.setTimeoutMs(dto.getTimeoutMs());
        fx.setMonitored(dto.isActive());
        return fx;
    }

    public long getId() { return id.get(); }
    public void setId(long value) { id.set(value); }
    public LongProperty idProperty() { return id; }

    public String getName() { return name.get(); }
    public void setName(String value) { name.set(value); }
    public StringProperty nameProperty() { return name; }

    public String getTargetAddress() { return targetAddress.get(); }
    public void setTargetAddress(String value) { targetAddress.set(value); }
    public StringProperty targetAddressProperty() { return targetAddress; }

    public int getCheckIntervalSec() { return checkIntervalSec.get(); }
    public void setCheckIntervalSec(int value) { checkIntervalSec.set(value); }
    public IntegerProperty checkIntervalSecProperty() { return checkIntervalSec; }

    public int getTimeoutMs() { return timeoutMs.get(); }
    public void setTimeoutMs(int value) { timeoutMs.set(value); }
    public IntegerProperty timeoutMsProperty() { return timeoutMs; }

    public int getLatencyMs() { return latencyMs.get(); }
    public void setLatencyMs(int value) { latencyMs.set(value); }
    public IntegerProperty latencyMsProperty() { return latencyMs; }

    public String getStatus() { return status.get(); }
    public void setStatus(String value) { status.set(value); }
    public StringProperty statusProperty() { return status; }

    public String getStatusMessage() { return statusMessage.get(); }
    public void setStatusMessage(String value) { statusMessage.set(value); }
    public StringProperty statusMessageProperty() { return statusMessage; }

    public boolean isMonitored() { return monitored.get(); }
    public void setMonitored(boolean value) { monitored.set(value); }
    public BooleanProperty monitoredProperty() { return monitored; }
}
