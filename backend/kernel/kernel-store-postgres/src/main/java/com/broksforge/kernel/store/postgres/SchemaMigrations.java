package com.broksforge.kernel.store.postgres;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A minimal, dependency-free schema migrator. It applies an ordered list of versioned SQL files from
 * the classpath, recording applied versions in {@code kernel_schema_history} so migration is
 * idempotent and forward-only — the same discipline as Flyway, without the dependency weight, which
 * suits a kernel whose whole point is minimal dependencies.
 *
 * <p>Each migration runs in its own transaction; a failure rolls back that migration and stops. New
 * migrations are added by dropping a {@code V<n>__*.sql} file under {@code db/migration} and listing
 * it here.
 */
public final class SchemaMigrations {

    private record Migration(int version, String resource) {
    }

    private static final List<Migration> MIGRATIONS = List.of(
            new Migration(1, "db/migration/V1__kernel_log.sql"));

    private SchemaMigrations() {
    }

    /**
     * Applies all pending migrations. Idempotent.
     *
     * @param dataSource the data source
     */
    public static void apply(DataSource dataSource) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            ensureHistory(c);
            Set<Integer> applied = loadApplied(c);
            for (Migration m : MIGRATIONS) {
                if (applied.contains(m.version())) {
                    continue;
                }
                for (String statement : statements(readResource(m.resource()))) {
                    try (Statement s = c.createStatement()) {
                        s.execute(statement);
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO kernel_schema_history (version) VALUES (?)")) {
                    ps.setInt(1, m.version());
                    ps.executeUpdate();
                }
                c.commit();
            }
        } catch (SQLException e) {
            throw new PersistenceException("schema migration failed", e);
        }
    }

    private static void ensureHistory(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS kernel_schema_history ("
                    + "version INT PRIMARY KEY, applied_at TIMESTAMPTZ NOT NULL DEFAULT now())");
        }
        c.commit();
    }

    private static Set<Integer> loadApplied(Connection c) throws SQLException {
        Set<Integer> applied = new HashSet<>();
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT version FROM kernel_schema_history")) {
            while (rs.next()) {
                applied.add(rs.getInt(1));
            }
        }
        return applied;
    }

    private static String readResource(String path) {
        try (InputStream in = SchemaMigrations.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new PersistenceException("migration resource not found: " + path, null);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new PersistenceException("could not read migration: " + path, e);
        }
    }

    /**
     * Splits a SQL script into individual statements, dropping {@code --} comment lines and blanks.
     * The kernel's migrations contain no semicolons inside literals, so a simple split is sufficient
     * and keeps this migrator dependency-free.
     */
    private static List<String> statements(String script) {
        StringBuilder cleaned = new StringBuilder();
        for (String line : script.split("\n")) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }
            cleaned.append(line).append('\n');
        }
        List<String> statements = new ArrayList<>();
        for (String part : cleaned.toString().split(";")) {
            String statement = part.strip();
            if (!statement.isEmpty()) {
                statements.add(statement);
            }
        }
        return statements;
    }
}
