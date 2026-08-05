package com.broksforge.kernel.tck;

import com.broksforge.kernel.api.NodeId;
import com.broksforge.kernel.core.engine.ForgeKernel;
import com.broksforge.kernel.core.engine.KernelRuntime;
import com.broksforge.kernel.core.memory.InMemoryGraphIndex;
import com.broksforge.kernel.core.memory.InMemoryLog;
import com.broksforge.kernel.core.memory.InMemoryNameStore;
import com.broksforge.kernel.core.memory.InMemoryRevisionStore;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Runs the full {@link KernelContract} against the in-memory backend. A backend retains its store
 * instances across {@link Backend#open()} calls, so the recovery test observes data surviving a
 * runtime restart over the same storage.
 */
class InMemoryKernelContractTest extends KernelContract {

    @Override
    protected Backend newBackend() {
        return new Backend() {
            private final InMemoryLog log = new InMemoryLog();
            private final InMemoryRevisionStore revisions = new InMemoryRevisionStore();
            private final InMemoryGraphIndex graph = new InMemoryGraphIndex();
            private final InMemoryNameStore names = new InMemoryNameStore();

            @Override
            public ForgeKernel open() {
                return new KernelRuntime(log, revisions, graph, names, List.of(),
                        () -> new NodeId(UUID.randomUUID()), Clock.systemUTC());
            }
        };
    }
}
