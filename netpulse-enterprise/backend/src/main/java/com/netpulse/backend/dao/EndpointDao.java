package com.netpulse.backend.dao;

import com.netpulse.backend.config.DatabaseManager;
import com.netpulse.backend.exception.DataAccessException;
import com.netpulse.backend.model.Endpoint;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Data access for the {@code endpoints} table.
 *
 * <p>Every statement is a {@link PreparedStatement} with bound parameters, so
 * user-supplied names and addresses are never concatenated into SQL text -
 * that is the structural defence against SQL injection.</p>
 */
public class EndpointDao {

    private static final String COLUMNS =
            "id, name, target_address, check_interval_sec, timeout_ms, is_active, created_at";

    public List<Endpoint> findAll() {
        String sql = "SELECT " + COLUMNS + " FROM endpoints ORDER BY id";
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            List<Endpoint> result = new ArrayList<>();
            while (rs.next()) {
                result.add(map(rs));
            }
            return result;
        } catch (SQLException ex) {
            throw new DataAccessException("Unable to load endpoints", ex);
        }
    }

    public Optional<Endpoint> findById(long id) {
        String sql = "SELECT " + COLUMNS + " FROM endpoints WHERE id = ?";
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Unable to load endpoint " + id, ex);
        }
    }

    public boolean existsByName(String name) {
        String sql = "SELECT 1 FROM endpoints WHERE LOWER(name) = LOWER(?)";
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Unable to check endpoint name", ex);
        }
    }

    /** Returns the subset of {@code ids} that actually exist - one round trip instead of N. */
    public Set<Long> findExistingIds(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Set.of();
        }
        StringBuilder sql = new StringBuilder("SELECT id FROM endpoints WHERE id IN (");
        sql.append("?,".repeat(ids.size()));
        sql.setLength(sql.length() - 1);        // drop the trailing comma
        sql.append(')');

        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {

            int index = 1;
            for (Long id : ids) {
                ps.setLong(index++, id);        // placeholders only - still fully parameterized
            }
            try (ResultSet rs = ps.executeQuery()) {
                Set<Long> found = new HashSet<>();
                while (rs.next()) {
                    found.add(rs.getLong("id"));
                }
                return found;
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Unable to validate endpoint ids", ex);
        }
    }

    public Endpoint insert(Endpoint endpoint) {
        String sql = "INSERT INTO endpoints (name, target_address, check_interval_sec, timeout_ms, is_active) "
                   + "VALUES (?, ?, ?, ?, ?)";
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, endpoint.getName());
            ps.setString(2, endpoint.getTargetAddress());
            ps.setInt(3, endpoint.getCheckIntervalSec());
            ps.setInt(4, endpoint.getTimeoutMs());
            ps.setBoolean(5, endpoint.isActive());
            ps.executeUpdate();

            long newId;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new DataAccessException("Insert returned no generated key", null);
                }
                newId = keys.getLong(1);
            }
            // Re-read so the caller gets DB-populated defaults (created_at) as well.
            return findById(newId).orElseThrow(
                    () -> new DataAccessException("Inserted endpoint disappeared", null));
        } catch (SQLException ex) {
            throw new DataAccessException("Unable to insert endpoint", ex);
        }
    }

    /** @return true when a row was actually removed (cascades to latency_logs). */
    public boolean deleteById(long id) {
        String sql = "DELETE FROM endpoints WHERE id = ?";
        try (Connection con = DatabaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw new DataAccessException("Unable to delete endpoint " + id, ex);
        }
    }

    private Endpoint map(ResultSet rs) throws SQLException {
        Endpoint e = new Endpoint();
        e.setId(rs.getLong("id"));
        e.setName(rs.getString("name"));
        e.setTargetAddress(rs.getString("target_address"));
        e.setCheckIntervalSec(rs.getInt("check_interval_sec"));
        e.setTimeoutMs(rs.getInt("timeout_ms"));
        e.setActive(rs.getBoolean("is_active"));
        e.setCreatedAt(readTimestamp(rs, "created_at"));
        return e;
    }

    static OffsetDateTime readTimestamp(ResultSet rs, String column) throws SQLException {
        try {
            return rs.getObject(column, OffsetDateTime.class);
        } catch (SQLException | UnsupportedOperationException ex) {
            // MySQL's TIMESTAMP has no zone - fall back and attach the JVM offset.
            java.sql.Timestamp ts = rs.getTimestamp(column);
            return ts == null ? null : ts.toInstant().atOffset(OffsetDateTime.now().getOffset());
        }
    }
}
