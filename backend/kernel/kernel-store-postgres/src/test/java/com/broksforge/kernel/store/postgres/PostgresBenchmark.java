package com.broksforge.kernel.store.postgres;

import com.broksforge.kernel.api.ActorId;
import com.broksforge.kernel.api.Kind;
import com.broksforge.kernel.api.NodeId;
import com.broksforge.kernel.api.OrgId;
import com.broksforge.kernel.api.Revision;
import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.kernel.core.command.AppendCommand;
import com.broksforge.kernel.core.engine.ForgeKernel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * PostgreSQL performance benchmarks: append (one durable, hash-chained JDBC transaction per fact) and
 * startup/recovery (replaying the persisted log into fresh projections at open). Gated on
 * {@code KERNEL_BENCH=1} and a real database via {@code KERNEL_TEST_PG_URL}.
 */
@EnabledIfEnvironmentVariable(named = "KERNEL_BENCH", matches = "1")
class PostgresBenchmark {

    private static final OrgId ORG = OrgId.fromString("00000000-0000-0000-0000-0000000000be");
    private static final ActorId ACTOR = ActorId.of("system:bench");
    private static PGSimpleDataSource ds;

    @BeforeAll
    static void setup() {
        String url = System.getenv("KERNEL_TEST_PG_URL");
        assumeTrue(url != null && !url.isBlank(), "KERNEL_TEST_PG_URL not set; skipping PostgreSQL benchmark");
        ds = new PGSimpleDataSource();
        ds.setUrl(url);
        ds.setUser(System.getenv().getOrDefault("KERNEL_TEST_PG_USER", "postgres"));
        ds.setPassword(System.getenv().getOrDefault("KERNEL_TEST_PG_PASSWORD", "postgres"));
        PostgresKernels.migrate(ds);
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute("TRUNCATE kernel_log");
        } catch (SQLException e) {
            throw new PersistenceException("reset failed", e);
        }
    }

    private static Revision prompt(String text) {
        return Revision.leaf(Kind.ARTIFACT, "prompt", CanonicalValue.objectBuilder().put("text", text).build());
    }

    @Test
    @DisplayName("PostgreSQL benchmarks: durable append + startup/recovery")
    void benchmarks() {
        System.out.println("\n=== Forge Kernel — PostgreSQL benchmarks (real DB) ===");
        ForgeKernel kernel = PostgresKernels.open(ds, List.of(),
                () -> new NodeId(UUID.randomUUID()), Clock.systemUTC());

        int warmup = 50;
        int measure = 400;
        for (int i = 0; i < warmup; i++) {
            kernel.append(ORG, new AppendCommand.CreateNode(prompt("w" + i)), ACTOR);
        }
        long t0 = System.nanoTime();
        for (int i = 0; i < measure; i++) {
            kernel.append(ORG, new AppendCommand.CreateNode(prompt("m" + i)), ACTOR);
        }
        double elapsedMs = (System.nanoTime() - t0) / 1_000_000.0;
        System.out.printf("durable append (unpooled) %,10.0f ops/sec   (%d ops in %.1f ms, new connection + "
                        + "one JDBC txn each; a pooled DataSource is materially faster)%n",
                measure / (elapsedMs / 1000.0), measure, elapsedMs);

        long total = warmup + measure;
        long r0 = System.nanoTime();
        ForgeKernel reopened = PostgresKernels.open(ds, List.of(),
                () -> new NodeId(UUID.randomUUID()), Clock.systemUTC());
        double recoveryMs = (System.nanoTime() - r0) / 1_000_000.0;
        System.out.printf("startup/recovery        replayed %d entries in %.1f ms (%,.0f entries/sec)%n",
                total, recoveryMs, total / (recoveryMs / 1000.0));
        System.out.println("chain verified after recovery: " + reopened.verifyChain(ORG));
        System.out.println();
    }
}
