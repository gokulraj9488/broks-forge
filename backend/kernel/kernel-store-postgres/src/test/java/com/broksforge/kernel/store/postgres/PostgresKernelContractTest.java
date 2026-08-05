package com.broksforge.kernel.store.postgres;

import com.broksforge.kernel.api.NodeId;
import com.broksforge.kernel.core.engine.ForgeKernel;
import com.broksforge.kernel.tck.KernelContract;
import org.junit.jupiter.api.BeforeAll;
import org.postgresql.ds.PGSimpleDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs the full {@link KernelContract} against a real PostgreSQL, proving the PostgreSQL backend is
 * interchangeable with the in-memory backend (ADR-V2-0001) — including recovery, since reopening
 * replays the durable log.
 *
 * <p>The database is supplied via environment variables so the test needs no Docker/Testcontainers
 * dependency (which cannot resolve in an offline build): set {@code KERNEL_TEST_PG_URL} (and
 * optionally {@code KERNEL_TEST_PG_USER} / {@code KERNEL_TEST_PG_PASSWORD}) to point at a throwaway
 * PostgreSQL. When unset, the whole class skips cleanly, so an offline box stays green while CI — or
 * any developer with a Postgres — exercises the full contract against real persistence.
 */
class PostgresKernelContractTest extends KernelContract {

    private static String url;
    private static String user;
    private static String password;

    @BeforeAll
    static void requirePostgres() {
        url = System.getenv("KERNEL_TEST_PG_URL");
        user = System.getenv().getOrDefault("KERNEL_TEST_PG_USER", "postgres");
        password = System.getenv().getOrDefault("KERNEL_TEST_PG_PASSWORD", "postgres");
        assumeTrue(url != null && !url.isBlank(),
                "KERNEL_TEST_PG_URL not set; skipping PostgreSQL contract test");
    }

    private static PGSimpleDataSource dataSource() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(url);
        ds.setUser(user);
        ds.setPassword(password);
        return ds;
    }

    @Override
    protected Backend newBackend() {
        PGSimpleDataSource ds = dataSource();
        // Isolate each test: ensure the schema exists, then clear the log.
        PostgresKernels.migrate(ds);
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute("TRUNCATE kernel_log");
        } catch (SQLException e) {
            throw new PersistenceException("failed to reset kernel_log", e);
        }
        return () -> PostgresKernels.open(ds, List.of(), () -> new NodeId(UUID.randomUUID()), Clock.systemUTC());
    }
}
