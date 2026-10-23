package com.mearvk.securejdk.transition;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * The private, TLS-secured MySQL implementation of {@link FailedTransitionStore}.
 *
 * <p>Connection matches {@code jvm-config.xml <mysql-bridge>}: host/port,
 * database {@code jvm_operand}, and {@code tls=true} → the JDBC URL requires SSL
 * ({@code sslMode=REQUIRED}). The MySQL JDBC driver is loaded reflectively; if
 * it (or the server) is absent, construction throws and the caller falls back
 * to {@link LocalJsonlStore} — the failure store must never take down the
 * safe-trim run.
 */
public final class MySqlStore implements FailedTransitionStore {

    private final Connection conn;
    private final String label;

    public MySqlStore(String host, int port, String db, String user, String password,
                      boolean tls) throws SQLException {
        // Ensure the driver is present (com.mysql.cj.jdbc.Driver) before connecting.
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL JDBC driver not on classpath", e);
        }
        String url = "jdbc:mysql://" + host + ":" + port + "/" + db
                + "?sslMode=" + (tls ? "REQUIRED" : "DISABLED")
                + "&connectTimeout=2000&socketTimeout=4000&characterEncoding=UTF-8";
        Properties props = new Properties();
        props.setProperty("user", user);
        if (password != null) props.setProperty("password", password);
        this.conn = DriverManager.getConnection(url, props);
        this.label = "mysql://" + user + "@" + host + ":" + port + "/" + db + (tls ? " (TLS)" : "");
    }

    @Override
    public long record(FailedTransition f) {
        String sql = """
            INSERT INTO failed_transitions
              (program_id, source_name, parse_digest, region_id, region_name,
               reason, detail, mm_globals, mm_functions, mm_code_len,
               mm_max_threads, mm_locks, mm_mailboxes, mm_est_heap,
               client_key, transport, status)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?, 'NEW')
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, f.programId());
            ps.setString(2, f.sourceName());
            ps.setString(3, f.parseDigest());
            if (f.regionId() != null) ps.setLong(4, f.regionId()); else ps.setNull(4, Types.BIGINT);
            ps.setString(5, f.regionName());
            ps.setString(6, mapReason(f.reason()));
            ps.setString(7, f.detail());
            ps.setInt(8, f.mmGlobals());
            ps.setInt(9, f.mmFunctions());
            ps.setInt(10, f.mmCodeLen());
            ps.setInt(11, f.mmMaxThreads());
            ps.setInt(12, f.mmLocks());
            ps.setInt(13, f.mmMailboxes());
            ps.setLong(14, f.mmEstHeap());
            ps.setString(15, f.clientKeyHex());
            ps.setString(16, f.transport());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("MySqlStore.record failed", e);
        }
    }

    @Override
    public List<FailedTransition> list(String status, int limit) {
        String sql = "SELECT * FROM failed_transitions WHERE status = ? ORDER BY created_at DESC LIMIT ?";
        List<FailedTransition> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(fromRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("MySqlStore.list failed", e);
        }
        return out;
    }

    @Override
    public FailedTransition get(long id) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM failed_transitions WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? fromRow(rs) : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("MySqlStore.get failed", e);
        }
    }

    @Override
    public boolean review(long id, String newStatus, String admin, String note) {
        try {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE failed_transitions SET status=?, admin_note=?, reviewed_by=?, reviewed_at=? WHERE id=?")) {
                ps.setString(1, newStatus);
                ps.setString(2, note);
                ps.setString(3, admin);
                ps.setTimestamp(4, Timestamp.from(Instant.now()));
                ps.setLong(5, id);
                int n = ps.executeUpdate();
                try (PreparedStatement a = conn.prepareStatement(
                        "INSERT INTO admin_audit (admin, action, transition_id, detail) VALUES (?,?,?,?)")) {
                    a.setString(1, admin);
                    a.setString(2, newStatus);
                    a.setLong(3, id);
                    a.setString(4, note);
                    a.executeUpdate();
                }
                conn.commit();
                return n > 0;
            }
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignore) {}
            throw new RuntimeException("MySqlStore.review failed", e);
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignore) {}
        }
    }

    @Override public String describe() { return label; }

    @Override public void close() {
        try { conn.close(); } catch (SQLException ignore) {}
    }

    // ---- helpers ----
    private static String mapReason(String r) {
        return switch (r) {
            case Stp.R_BUDGET_EXCEEDED, Stp.R_GRADE_DENIED, Stp.R_UNKNOWN_PEER, Stp.R_POLICY,
                 Stp.R_CRYPTO_FAIL, Stp.R_TIMEOUT, Stp.R_UNREACHABLE, Stp.R_UNSUPPORTED_VERSION -> r;
            default -> "OTHER";
        };
    }

    private static FailedTransition fromRow(ResultSet rs) throws SQLException {
        long rid = rs.getLong("region_id");
        Long regionId = rs.wasNull() ? null : rid;
        return new FailedTransition(
                rs.getLong("id"),
                rs.getString("program_id"),
                rs.getString("source_name"),
                rs.getString("parse_digest"),
                regionId,
                rs.getString("region_name"),
                rs.getString("reason"),
                rs.getString("detail"),
                rs.getInt("mm_globals"),
                rs.getInt("mm_functions"),
                rs.getInt("mm_code_len"),
                rs.getInt("mm_max_threads"),
                rs.getInt("mm_locks"),
                rs.getInt("mm_mailboxes"),
                rs.getLong("mm_est_heap"),
                rs.getString("client_key"),
                rs.getString("transport"),
                rs.getString("status"));
    }
}
