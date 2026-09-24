package com.netpulse.backend.dao;

import com.netpulse.backend.config.DatabaseManager;
import com.netpulse.backend.exception.DataAccessException;
import com.netpulse.backend.model.LatencyLog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Data access for the time-series {@code latency_logs} table. */
public class LatencyLogDao {

    /**
     * Inserts a whole batch inside one transaction using JDBC batching.
     *
     * <p>Round trips are the bottleneck here: 200 single inserts cost 200
     * network hops, one {@code executeBatch} costs roughly one. If any row
     * fails the entire batch is rolled back, so a partially written sample
     * window can never reach the chart.</p>
     *
     * @return number of rows written
     */
    public int insertBatch(List<LatencyLog> logs) {
        if (logs.isEmpty()) {
            return 0;
        }
        String sql = "INSERT INTO latency_logs "
                   + "(endpoint_id, latency_ms, is_reachable, status_message, recorded_at) "
                   + "VALUES (?, ?, ?, ?, ?)";

        Connection con = null;
        try {
            con = DatabaseManager.getConnection();
            con.setAutoCommit(false);

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                for (LatencyLog log : logs) {
                    ps.setLong(1, log.getEndpointId());
                    if (log.getLatencyMs() == null) {
                        ps.setNull(2, Types.INTEGER);
                    } else {
                        ps.setInt(2, log.getLatencyMs());
                    }
                    ps.setBoolean(3, log.isReachable());
                    ps.setString(4, truncate(log.getStatusMessage()));
                    OffsetDateTime when = log.getRecordedAt() == null
                            ? OffsetDateTime.now()
                            : log.getRecordedAt();
                    ps.setObject(5, when);
                    ps.addBatch();
                }
                int[] counts = ps.executeBatch();
                con.commit();
                return counts.length;
            } catch (SQLException ex) {
                con.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Unable to persist metric batch", ex);
        } finally {
            closeQuietly(con);
        }
    }

    /**
     * Returns the most recent {@code limit} samples for one endpoint, ordered
     * oldest-first so the caller can feed them straight into a chart series.
     * Backed by {@code idx_latency_logs_endpoint_recorded}.
     */
    public List<LatencyLog> findRecent(long endpointId, int limit) {
        String sql = "SELECT id, endpoint_id, latency_ms, is_reachable, status_message, recorded_at "
                   + "FROM latency_logs WHERE endpoint_id = ? "
                   + "ORDER BY recorded_at DESC, id DESC LIMIT ?";

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setLong(1, endpointId);
            ps.setInt(2, limit);

            try (ResultSet rs = ps.executeQuery()) {
                List<LatencyLog> rows = new ArrayList<>();
                while (rs.next()) {
                    LatencyLog log = new LatencyLog();
                    log.setId(rs.getLong("id"));
                    log.setEndpointId(rs.getLong("endpoint_id"));
                    int latency = rs.getInt("latency_ms");
                    log.setLatencyMs(rs.wasNull() ? null : latency);
                    log.setReachable(rs.getBoolean("is_reachable"));
                    log.setStatusMessage(rs.getString("status_message"));
                    log.setRecordedAt(EndpointDao.readTimestamp(rs, "recorded_at"));
                    rows.add(log);
                }
                Collections.reverse(rows);   // chronological for the chart
                return rows;
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Unable to load history for endpoint " + endpointId, ex);
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 255 ? message : message.substring(0, 255);
    }

    private void closeQuietly(Connection con) {
        if (con == null) {
            return;
        }
        try {
            con.setAutoCommit(true);
            con.close();
        } catch (SQLException ignored) {
            // returning the connection to the pool must never mask the real error
        }
    }
}
