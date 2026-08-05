package com.broksforge.kernel.core.engine;

import com.broksforge.kernel.api.NodeId;
import com.broksforge.kernel.core.memory.InMemoryGraphIndex;
import com.broksforge.kernel.core.memory.InMemoryLog;
import com.broksforge.kernel.core.memory.InMemoryNameStore;
import com.broksforge.kernel.core.memory.InMemoryRevisionStore;
import com.broksforge.kernel.core.reproduce.Reproducer;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Factory for assembling ready-to-use kernels. The in-memory kernel is a fully functioning Forge
 * Kernel with no infrastructure — it could be released as a standalone open-source library.
 */
public final class Kernels {

    private Kernels() {
    }

    /**
     * @param reproducers the reproducers to register (may be empty)
     * @return an in-memory kernel with random node-id minting and a UTC clock
     */
    public static ForgeKernel inMemory(Reproducer... reproducers) {
        return inMemory(() -> new NodeId(UUID.randomUUID()), Clock.systemUTC(), List.of(reproducers));
    }

    /**
     * Assembles an in-memory kernel with explicit minting and clock — used by tests for determinism.
     *
     * @param minter      supplies fresh node ids
     * @param clock       supplies record time
     * @param reproducers the reproducers to register
     * @return the kernel
     */
    public static ForgeKernel inMemory(Supplier<NodeId> minter, Clock clock, List<Reproducer> reproducers) {
        return new KernelRuntime(
                new InMemoryLog(),
                new InMemoryRevisionStore(),
                new InMemoryGraphIndex(),
                new InMemoryNameStore(),
                reproducers,
                minter,
                clock);
    }
}
