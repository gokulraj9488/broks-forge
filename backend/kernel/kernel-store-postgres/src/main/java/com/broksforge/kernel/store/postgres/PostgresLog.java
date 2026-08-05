package com.broksforge.kernel.store.postgres;

import com.broksforge.kernel.api.LogPosition;
import com.broksforge.kernel.api.OrgId;
import com.broksforge.kernel.api.RevisionHash;
import com.broksforge.kernel.core.codec.LogEntryCodec;
import com.broksforge.kernel.core.log.LogEntry;
import com.broksforge.kernel.core.store.Log;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * PostgreSQL-backed {@link Log} — the durable source of truth. Each append is one JDBC transaction:
 * read the org's current head (position + chain hash), let the caller seal the entry with them, and
 * insert it. The {@code (org, position)} primary key is the ultimate guard against a duplicate slot.
 *
 * <p>Uses plain JDBC only — no Spring, no ORM. The full entry is stored as canonical JSON
 * ({@link LogEntryCodec}), so the log alone suffices to rebuild every projection.
 */
public final class PostgresLog implements Log {

    private final DataSource dataSource;

    public PostgresLog(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public LogEntry append(OrgId org, Sealer sealer) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                long maxPosition = 0;
                RevisionHash prevHash = null;
                try (PreparedStatement head = c.prepareStatement(
                        "SELECT position, entry_hash FROM kernel_log WHERE org = ? ORDER BY position DESC LIMIT 1")) {
                    head.setString(1, org.toString());
                    try (ResultSet rs = head.executeQuery()) {
                        if (rs.next()) {
                            maxPosition = rs.getLong(1);
                            prevHash = RevisionHash.parse(rs.getString(2));
                        }
                    }
                }
                LogPosition position = new LogPosition(maxPosition + 1);
                LogEntry entry = sealer.seal(position, prevHash);
                String json = new String(LogEntryCodec.encode(entry), StandardCharsets.UTF_8);
                try (PreparedStatement ins = c.prepareStatement(
                        "INSERT INTO kernel_log (org, position, entry_hash, entry_json) VALUES (?, ?, ?, ?)")) {
                    ins.setString(1, org.toString());
                    ins.setLong(2, position.value());
                    ins.setString(3, entry.entryHash().toString());
                    ins.setString(4, json);
                    ins.executeUpdate();
                }
                c.commit();
                return entry;
            } catch (RuntimeException | SQLException e) {
                c.rollback();
                throw e instanceof SQLException sql
                        ? new PersistenceException("append failed for org " + org, sql)
                        : (RuntimeException) e;
            }
        } catch (SQLException e) {
            throw new PersistenceException("append failed for org " + org, e);
        }
    }

    @Override
    public LogPosition head(OrgId org) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COALESCE(MAX(position), 0) FROM kernel_log WHERE org = ?")) {
            ps.setString(1, org.toString());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new LogPosition(rs.getLong(1));
            }
        } catch (SQLException e) {
            throw new PersistenceException("head failed for org " + org, e);
        }
    }

    @Override
    public List<LogEntry> read(OrgId org, LogPosition fromExclusive, LogPosition toInclusive) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT entry_json FROM kernel_log WHERE org = ? AND position > ? AND position <= ? "
                             + "ORDER BY position")) {
            ps.setString(1, org.toString());
            ps.setLong(2, fromExclusive.value());
            ps.setLong(3, toInclusive.value());
            return decodeAll(ps);
        } catch (SQLException e) {
            throw new PersistenceException("read failed for org " + org, e);
        }
    }

    @Override
    public List<LogEntry> all(OrgId org) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT entry_json FROM kernel_log WHERE org = ? ORDER BY position")) {
            ps.setString(1, org.toString());
            return decodeAll(ps);
        } catch (SQLException e) {
            throw new PersistenceException("all failed for org " + org, e);
        }
    }

    /**
     * @return the distinct organizations that have entries (used to replay every org at open)
     */
    public List<OrgId> organizations() {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT DISTINCT org FROM kernel_log");
             ResultSet rs = ps.executeQuery()) {
            List<OrgId> orgs = new ArrayList<>();
            while (rs.next()) {
                orgs.add(OrgId.fromString(rs.getString(1)));
            }
            return orgs;
        } catch (SQLException e) {
            throw new PersistenceException("organizations query failed", e);
        }
    }

    private static List<LogEntry> decodeAll(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            List<LogEntry> entries = new ArrayList<>();
            while (rs.next()) {
                entries.add(LogEntryCodec.decode(rs.getString(1).getBytes(StandardCharsets.UTF_8)));
            }
            return entries;
        }
    }
}
