package com.netpulse.client;

import com.netpulse.client.ui.DashboardController;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;

/**
 * JavaFX entry point for the NetPulse Enterprise desktop client.
 *
 * <p>The backend location is read from {@code NETPULSE_API_URL} (or the
 * {@code netpulse.api.url} system property) so the same build runs against a
 * local server or a remote one without recompiling.</p>
 */
public class MainApp extends Application {

    private DashboardController controller;

    @Override
    public void start(Stage stage) {
        String apiBaseUrl = resolveApiUrl();

        controller = new DashboardController(apiBaseUrl);
        Scene scene = new Scene(controller.getRoot(), 1180, 760);

        URL css = MainApp.class.getResource("/styles.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }

        stage.setTitle("NetPulse Enterprise - Live Network Health Dashboard  [" + apiBaseUrl + "]");
        stage.setScene(scene);
        stage.setMinWidth(980);
        stage.setMinHeight(640);

        // Clean lifecycle: stop the schedulers before the window disappears so
        // no worker thread outlives the scene graph it reports into.
        stage.setOnCloseRequest(event -> controller.shutdown());

        stage.show();
        controller.initialize();
    }

    /** Also called when the platform exits through other paths; shutdown is idempotent. */
    @Override
    public void stop() {
        if (controller != null) {
            controller.shutdown();
        }
    }

    private String resolveApiUrl() {
        String url = System.getenv("NETPULSE_API_URL");
        if (url == null || url.isBlank()) {
            url = System.getProperty("netpulse.api.url");
        }
        return (url == null || url.isBlank()) ? "http://localhost:7070" : url.trim();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
