package com.broksforge.kernel.store.postgres;

import com.broksforge.kernel.api.NodeId;
import com.broksforge.kernel.api.OrgId;
import com.broksforge.kernel.core.engine.ForgeKernel;
import com.broksforge.kernel.core.engine.KernelRuntime;
import com.broksforge.kernel.core.log.LogEntry;
import com.broksforge.kernel.core.log.Payload;
import com.broksforge.kernel.core.memory.InMemoryGraphIndex;
import com.broksforge.kernel.core.memory.InMemoryNameStore;
import com.broksforge.kernel.core.memory.InMemoryRevisionStore;
import com.broksforge.kernel.core.reproduce.Reproducer;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Assembles a PostgreSQL-backed Forge Kernel. The log is durable ({@link PostgresLog}); the three
 * projections are in-memory views rebuilt by replaying the persisted log at open — the constitutional
 * model where projections are derivations of the one true log (ADR-V2-0001). Reopening after a
 * restart replays the same log and restores identical state, which is what makes recovery work.
 */
public final class PostgresKernels {

    private PostgresKernels() {
    }

    /**
     * Runs migrations and opens a kernel over the given data source, with random node-id minting and
     * a UTC clock.
     *
     * @param dataSource  the PostgreSQL data source
     * @param reproducers reproducers to register
     * @return the kernel
     */
    public static ForgeKernel open(DataSource dataSource, List<Reproducer> reproducers) {
        return open(dataSource, reproducers, () -> new NodeId(UUID.randomUUID()), Clock.systemUTC());
    }

    /**
     * Runs migrations and opens a kernel with explicit minting and clock.
     *
     * @param dataSource  the PostgreSQL data source
     * @param reproducers reproducers to register
     * @param minter      node-id source
     * @param clock       record-time source
     * @return the kernel
     */
    public static ForgeKernel open(DataSource dataSource, List<Reproducer> reproducers,
                                   Supplier<NodeId> minter, Clock clock) {
        migrate(dataSource);
        PostgresLog log = new PostgresLog(dataSource);

        InMemoryRevisionStore revisions = new InMemoryRevisionStore();
        InMemoryGraphIndex graph = new InMemoryGraphIndex();
        InMemoryNameStore names = new InMemoryNameStore();
        for (OrgId org : log.organizations()) {
            for (LogEntry entry : log.all(org)) {
                if (entry.payload() instanceof Payload.NodePut np) {
                    revisions.put(np.revision().hash(), np.revision());
                }
                graph.apply(entry);
                names.apply(entry);
            }
        }
        return new KernelRuntime(log, revisions, graph, names, reproducers, minter, clock);
    }

    /**
     * Applies the kernel schema migrations. Idempotent.
     *
     * @param dataSource the data source
     */
    public static void migrate(DataSource dataSource) {
        SchemaMigrations.apply(dataSource);
    }
}
