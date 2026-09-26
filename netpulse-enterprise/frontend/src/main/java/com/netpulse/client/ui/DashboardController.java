package com.netpulse.client.ui;

import com.netpulse.client.model.EndpointDto;
import com.netpulse.client.model.EndpointFx;
import com.netpulse.client.net.ApiClient;
import com.netpulse.client.probe.ProbeResult;
import com.netpulse.client.probe.ProbeScheduler;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Separator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the dashboard scene graph programmatically and mediates between the
 * UI, the {@link ProbeScheduler} and the {@link ApiClient}.
 *
 * <p><b>Threading contract:</b> every method in this class that mutates a node
 * or an observable model runs on the FX Application Thread. Callbacks arriving
 * from worker threads ({@link #onProbeResult}, API futures) do nothing except
 * hand a snapshot to {@link Platform#runLater(Runnable)}.</p>
 */
public class DashboardController {

    /** Chart memory guard: at most this many points survive per series. */
    private static final int MAX_POINTS_PER_SERIES = 20;
    /** Latency above this is rendered as degraded rather than healthy. */
    private static final int SLOW_THRESHOLD_MS = 300;
    private static final int WORKER_THREADS = 8;

    private final ApiClient apiClient;
    private final ProbeScheduler scheduler;

    private final ObservableList<EndpointFx> endpoints = FXCollections.observableArrayList();
    private final Map<Long, EndpointFx> endpointsById = new HashMap<>();
    private final Map<Long, XYChart.Series<Number, Number>> seriesById = new HashMap<>();

    private final BorderPane root = new BorderPane();
    private final TableView<EndpointFx> table = new TableView<>();
    private final LineChart<Number, Number> chart;
    private final NumberAxis xAxis = new NumberAxis();
    private final NumberAxis yAxis = new NumberAxis();

    private final TextField nameField = new TextField();
    private final TextField addressField = new TextField();
    private final TextField intervalField = new TextField("5");
    private final TextField timeoutField = new TextField("1500");

    private final Button addButton = new Button("Add Target");
    private final Button removeButton = new Button("Remove Selected");
    private final Button startButton = new Button("Start Monitoring");
    private final Button stopButton = new Button("Stop");
    private final Button refreshButton = new Button("Reload From API");

    private final Label statusLabel = new Label("Idle");
    private final Label metricsLabel = new Label("");

    /** Wall-clock origin of the current monitoring session; the chart's x=0. */
    private long sessionStartEpochMillis = System.currentTimeMillis();

    public DashboardController(String apiBaseUrl) {
        this.apiClient = new ApiClient(apiBaseUrl);
        this.scheduler = new ProbeScheduler(
                apiClient,
                WORKER_THREADS,
                this::onProbeResult,                                  // background thread
                message -> Platform.runLater(() -> statusLabel.setText(message)));

        this.chart = new LineChart<>(xAxis, yAxis);
        buildLayout();
    }

    public BorderPane getRoot() {
        return root;
    }

    // =================================================================
    // Layout
    // =================================================================

    private void buildLayout() {
        root.getStyleClass().add("root");
        root.setTop(buildHeader());
        root.setCenter(buildCenter());
        root.setBottom(buildStatusBar());
    }

    private Region buildHeader() {
        Label title = new Label("NetPulse Enterprise");
        title.getStyleClass().add("app-title");
        Label subtitle = new Label("Distributed real-time network health monitor");
        subtitle.getStyleClass().add("subtle");

        VBox titleBox = new VBox(2, title, subtitle);

        nameField.setPromptText("Display name");
        nameField.setPrefWidth(180);
        addressField.setPromptText("https://host  or  host:port");
        addressField.setPrefWidth(260);
        intervalField.setPrefWidth(70);
        intervalField.setTooltip(new Tooltip("Probe interval in seconds"));
        timeoutField.setPrefWidth(80);
        timeoutField.setTooltip(new Tooltip("Connection timeout in milliseconds"));

        addButton.getStyleClass().add("button-primary");
        removeButton.getStyleClass().add("button-danger");
        startButton.getStyleClass().add("button-primary");

        addButton.setOnAction(e -> handleAddTarget());
        removeButton.setOnAction(e -> handleRemoveSelected());
        startButton.setOnAction(e -> handleStart());
        stopButton.setOnAction(e -> handleStop());
        refreshButton.setOnAction(e -> loadEndpointsFromBackend());
        stopButton.setDisable(true);

        HBox form = new HBox(8,
                labelled("Name", nameField),
                labelled("Target", addressField),
                labelled("Interval (s)", intervalField),
                labelled("Timeout (ms)", timeoutField),
                bottomAligned(addButton),
                bottomAligned(removeButton));
        form.setAlignment(Pos.BOTTOM_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox controls = new HBox(8, startButton, stopButton, refreshButton);
        controls.setAlignment(Pos.CENTER_RIGHT);

        HBox topRow = new HBox(16, titleBox, spacer, controls);
        topRow.setAlignment(Pos.CENTER_LEFT);

        VBox header = new VBox(12, topRow, new Separator(), form);
        header.getStyleClass().add("header-bar");
        header.setPadding(new Insets(14, 16, 14, 16));
        return header;
    }

    private Region buildCenter() {
        configureTable();
        configureChart();

        VBox.setVgrow(table, Priority.ALWAYS);
        VBox box = new VBox(10, table, chart);
        box.setPadding(new Insets(12, 16, 8, 16));
        chart.setMinHeight(260);
        chart.setPrefHeight(300);
        return box;
    }

    private Region buildStatusBar() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(12, statusLabel, spacer, metricsLabel);
        bar.getStyleClass().add("status-bar");
        bar.setPadding(new Insets(7, 16, 7, 16));
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private VBox labelled(String text, Region field) {
        Label label = new Label(text);
        label.getStyleClass().add("section-label");
        return new VBox(4, label, field);
    }

    private VBox bottomAligned(Button button) {
        VBox box = new VBox(button);
        box.setAlignment(Pos.BOTTOM_LEFT);
        box.setPadding(new Insets(0, 0, 1, 0));
        return box;
    }

    // -----------------------------------------------------------------
    // Table
    // -----------------------------------------------------------------
    private void configureTable() {
        TableColumn<EndpointFx, String> nameCol = new TableColumn<>("Target Name");
        nameCol.setCellValueFactory(cd -> cd.getValue().nameProperty());
        nameCol.setPrefWidth(190);

        TableColumn<EndpointFx, String> addressCol = new TableColumn<>("Address");
        addressCol.setCellValueFactory(cd -> cd.getValue().targetAddressProperty());
        addressCol.setPrefWidth(300);

        TableColumn<EndpointFx, Number> latencyCol = new TableColumn<>("Latency (ms)");
        latencyCol.setCellValueFactory(cd -> cd.getValue().latencyMsProperty());
        latencyCol.setPrefWidth(110);
        latencyCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null || value.intValue() == EndpointFx.NO_LATENCY) {
                    setText(empty ? null : "--");
                } else {
                    setText(value.intValue() + " ms");
                }
            }
        });

        TableColumn<EndpointFx, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cd -> cd.getValue().statusProperty());
        statusCol.setPrefWidth(110);
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                getStyleClass().removeAll("status-online", "status-slow", "status-offline", "status-pending");
                if (empty || status == null) {
                    setText(null);
                    return;
                }
                setText(status);
                switch (status) {
                    case "ONLINE"  -> getStyleClass().add("status-online");
                    case "SLOW"    -> getStyleClass().add("status-slow");
                    case "OFFLINE" -> getStyleClass().add("status-offline");
                    default        -> getStyleClass().add("status-pending");
                }
            }
        });

        TableColumn<EndpointFx, String> messageCol = new TableColumn<>("Last Response");
        messageCol.setCellValueFactory(cd -> cd.getValue().statusMessageProperty());
        messageCol.setPrefWidth(240);

        table.getColumns().addAll(nameCol, addressCol, latencyCol, statusCol, messageCol);
        table.setItems(endpoints);
        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No targets yet - add one above or reload from the API."));
        table.setPrefHeight(260);
    }

    // -----------------------------------------------------------------
    // Chart
    // -----------------------------------------------------------------
    private void configureChart() {
        xAxis.setLabel("Seconds since monitoring started");
        xAxis.setForceZeroInRange(false);
        yAxis.setLabel("Latency (ms)");
        yAxis.setForceZeroInRange(true);

        chart.setTitle("Live latency / jitter");
        chart.getStyleClass().add("chart");

        // --- rendering performance -------------------------------------
        // Animations queue a Timeline per data mutation. With several series
        // updating every second that alone is enough to drop frames, so every
        // animation in the chart hierarchy is switched off. Symbols are dropped
        // too: each one is a Node, and pruning would leave orphaned nodes behind.
        chart.setAnimated(false);
        chart.setCreateSymbols(false);
        xAxis.setAnimated(false);
        yAxis.setAnimated(false);
        chart.setLegendVisible(true);
    }

    // =================================================================
    // Lifecycle
    // =================================================================

    /** Called once after the stage is shown. */
    public void initialize() {
        loadEndpointsFromBackend();
        startUiTicker();
    }

    /** Refreshes the status bar counters without coupling them to probe events. */
    private void startUiTicker() {
        javafx.animation.Timeline ticker = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), e -> metricsLabel.setText(
                        "probes: " + scheduler.getProbesCompleted()
                                + "   uploaded: " + scheduler.getSamplesUploaded()
                                + "   queued: " + scheduler.getPendingUploadCount()
                                + "   dropped: " + scheduler.getSamplesDropped())));
        ticker.setCycleCount(javafx.animation.Animation.INDEFINITE);
        ticker.play();
    }

    /** Idempotent; invoked from both the close handler and {@code Application.stop()}. */
    public void shutdown() {
        scheduler.shutdown();
        apiClient.close();
    }

    // =================================================================
    // Actions
    // =================================================================

    private void loadEndpointsFromBackend() {
        statusLabel.setText("Loading targets from backend...");
        apiClient.listEndpoints()
                .thenAccept(list -> Platform.runLater(() -> applyEndpointList(list)))
                .exceptionally(ex -> {
                    Platform.runLater(() -> statusLabel.setText(
                            "Backend unreachable: " + rootMessage(ex) + " - working offline"));
                    return null;
                });
    }

    private void applyEndpointList(List<EndpointDto> list) {
        endpoints.clear();
        endpointsById.clear();
        seriesById.clear();
        chart.getData().clear();
        scheduler.clearTargets();

        if (list != null) {
            for (EndpointDto dto : list) {
                EndpointFx fx = EndpointFx.from(dto);
                endpoints.add(fx);
                endpointsById.put(fx.getId(), fx);
                if (fx.isMonitored()) {
                    scheduler.registerTarget(fx.getId(), fx.getName(), fx.getTargetAddress(),
                            fx.getTimeoutMs(), fx.getCheckIntervalSec());
                }
            }
        }
        statusLabel.setText(endpoints.size() + " target(s) loaded");
    }

    private void handleAddTarget() {
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        String address = addressField.getText() == null ? "" : addressField.getText().trim();

        if (name.isEmpty() || address.isEmpty()) {
            warn("Both a name and a target address are required.");
            return;
        }
        int interval;
        int timeout;
        try {
            interval = Integer.parseInt(intervalField.getText().trim());
            timeout = Integer.parseInt(timeoutField.getText().trim());
        } catch (NumberFormatException ex) {
            warn("Interval and timeout must be whole numbers.");
            return;
        }
        if (timeout > interval * 1000) {
            warn("Timeout must not exceed the probe interval, otherwise probes pile up.");
            return;
        }

        addButton.setDisable(true);
        statusLabel.setText("Creating target...");

        apiClient.createEndpoint(new EndpointDto(name, address, interval, timeout))
                .thenAccept(created -> Platform.runLater(() -> {
                    addButton.setDisable(false);
                    if (created == null) {
                        warn("Backend returned no endpoint payload.");
                        return;
                    }
                    EndpointFx fx = EndpointFx.from(created);
                    endpoints.add(fx);
                    endpointsById.put(fx.getId(), fx);
                    scheduler.registerTarget(fx.getId(), fx.getName(), fx.getTargetAddress(),
                            fx.getTimeoutMs(), fx.getCheckIntervalSec());
                    nameField.clear();
                    addressField.clear();
                    statusLabel.setText("Added '" + fx.getName() + "'");
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        addButton.setDisable(false);
                        warn("Could not create target: " + rootMessage(ex));
                    });
                    return null;
                });
    }

    private void handleRemoveSelected() {
        EndpointFx selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            warn("Select a row first.");
            return;
        }
        long id = selected.getId();
        removeButton.setDisable(true);

        apiClient.deleteEndpoint(id)
                .thenRun(() -> Platform.runLater(() -> {
                    removeButton.setDisable(false);
                    scheduler.unregisterTarget(id);
                    endpoints.remove(selected);
                    endpointsById.remove(id);
                    XYChart.Series<Number, Number> series = seriesById.remove(id);
                    if (series != null) {
                        chart.getData().remove(series);
                    }
                    statusLabel.setText("Removed '" + selected.getName() + "'");
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        removeButton.setDisable(false);
                        warn("Could not remove target: " + rootMessage(ex));
                    });
                    return null;
                });
    }

    private void handleStart() {
        if (endpoints.isEmpty()) {
            warn("Add at least one target before starting.");
            return;
        }
        sessionStartEpochMillis = System.currentTimeMillis();
        chart.getData().clear();
        seriesById.clear();

        scheduler.start();
        startButton.setDisable(true);
        stopButton.setDisable(false);
        refreshButton.setDisable(true);

        seedChartFromHistory();
    }

    private void handleStop() {
        scheduler.stop();
        startButton.setDisable(false);
        stopButton.setDisable(true);
        refreshButton.setDisable(false);
    }

    /**
     * Pre-fills each series with the last stored samples so the chart is useful
     * immediately instead of after 20 live probes. Historical points sit at
     * negative x values (seconds before the session start).
     */
    private void seedChartFromHistory() {
        for (EndpointFx endpoint : endpoints) {
            if (!endpoint.isMonitored()) {
                continue;
            }
            long id = endpoint.getId();
            apiClient.history(id, MAX_POINTS_PER_SERIES)
                    .thenAccept(samples -> Platform.runLater(() -> {
                        if (samples == null || samples.isEmpty()) {
                            return;
                        }
                        XYChart.Series<Number, Number> series = seriesFor(id, endpoint.getName());
                        samples.stream()
                                .filter(s -> s.isReachable() && s.getLatencyMs() != null && s.getRecordedAt() != null)
                                .forEach(s -> series.getData().add(new XYChart.Data<>(
                                        elapsedSeconds(s.getRecordedAt()), s.getLatencyMs())));
                        prune(series);
                    }))
                    .exceptionally(ex -> null);   // history is a nicety, never fatal
        }
    }

    // =================================================================
    // Probe results (worker thread -> FX thread)
    // =================================================================

    /**
     * Invoked on a probe worker thread. It performs no UI work itself; it only
     * schedules {@link #applyResult(ProbeResult)} onto the FX Application Thread.
     */
    private void onProbeResult(ProbeResult result) {
        Platform.runLater(() -> applyResult(result));
    }

    /** FX Application Thread only. */
    private void applyResult(ProbeResult result) {
        EndpointFx endpoint = endpointsById.get(result.getEndpointId());
        if (endpoint == null) {
            return;     // target was removed while its probe was in flight
        }

        if (result.isReachable() && result.getLatencyMs() != null) {
            int latency = result.getLatencyMs();
            endpoint.setLatencyMs(latency);
            endpoint.setStatus(latency > SLOW_THRESHOLD_MS ? "SLOW" : "ONLINE");
            endpoint.setStatusMessage(result.getStatusMessage());

            XYChart.Series<Number, Number> series = seriesFor(endpoint.getId(), endpoint.getName());
            series.getData().add(new XYChart.Data<>(elapsedSeconds(result.getRecordedAt()), latency));
            prune(series);
        } else {
            endpoint.setLatencyMs(EndpointFx.NO_LATENCY);
            endpoint.setStatus("OFFLINE");
            endpoint.setStatusMessage(result.getStatusMessage());

            // Plot the outage as a zero so the gap is visible in the trend line.
            XYChart.Series<Number, Number> series = seriesFor(endpoint.getId(), endpoint.getName());
            series.getData().add(new XYChart.Data<>(elapsedSeconds(result.getRecordedAt()), 0));
            prune(series);
        }
    }

    private XYChart.Series<Number, Number> seriesFor(long endpointId, String name) {
        return seriesById.computeIfAbsent(endpointId, id -> {
            XYChart.Series<Number, Number> series = new XYChart.Series<>();
            series.setName(name);
            chart.getData().add(series);
            return series;
        });
    }

    /**
     * Chart memory optimization: each {@code XYChart.Data} keeps a Node and
     * listeners alive, so an unbounded series leaks steadily during a long run.
     * Trimming from the head keeps the window fixed and the render cost flat.
     */
    private void prune(XYChart.Series<Number, Number> series) {
        while (series.getData().size() > MAX_POINTS_PER_SERIES) {
            series.getData().remove(0);
        }
    }

    private double elapsedSeconds(OffsetDateTime when) {
        long millis = when.toInstant().toEpochMilli() - sessionStartEpochMillis;
        return Math.round(millis / 100.0) / 10.0;      // one decimal place
    }

    // =================================================================
    // helpers
    // =================================================================

    private void warn(String message) {
        statusLabel.setText(message);
        Alert alert = new Alert(Alert.AlertType.WARNING, message);
        alert.setHeaderText(null);
        alert.setTitle("NetPulse");
        alert.initOwner(root.getScene() == null ? null : root.getScene().getWindow());
        alert.show();
    }

    private String rootMessage(Throwable t) {
        Throwable current = t;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String msg = current.getMessage();
        return msg == null ? current.getClass().getSimpleName() : msg;
    }
}
