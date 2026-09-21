package com.netpulse.backend.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Owns the single JDBC connection pool for the whole process.
 *
 * <p>A pool matters here because the frontend pushes batched metrics from a
 * worker pool: several REST requests can hit the DAO layer at the same instant,
 * and opening a raw {@code DriverManager} connection per request would dominate
 * the request latency.</p>
 */
public final class DatabaseManager {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseManager.class);

    private static HikariDataSource dataSource;

    private DatabaseManager() {
    }

    public static synchronized void init() {
        if (dataSource != null && !dataSource.isClosed()) {
            return;
        }
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(AppConfig.dbUrl());
        cfg.setUsername(AppConfig.dbUser());
        cfg.setPassword(AppConfig.dbPassword());
        cfg.setMaximumPoolSize(AppConfig.dbPoolSize());
        cfg.setMinimumIdle(2);
        cfg.setPoolName("netpulse-pool");
        cfg.setConnectionTimeout(5_000);   // fail fast instead of blocking a request thread
        cfg.setValidationTimeout(2_000);
        cfg.setIdleTimeout(60_000);
        cfg.setMaxLifetime(30 * 60_000L);

        dataSource = new HikariDataSource(cfg);
        LOG.info("JDBC pool ready -> {}", AppConfig.dbUrl());
    }

    /**
     * Borrows a pooled connection. Callers MUST close it (try-with-resources),
     * which returns it to the pool rather than tearing down the socket.
     */
    public static Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            init();
        }
        return dataSource.getConnection();
    }

    /** Verifies the database is reachable; used by the startup self-check. */
    public static boolean healthCheck() {
        try (Connection con = getConnection()) {
            return con.isValid(2);
        } catch (SQLException ex) {
            LOG.error("Database health check failed: {}", ex.getMessage());
            return false;
        }
    }

    public static synchronized void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            LOG.info("JDBC pool closed");
        }
    }
}
