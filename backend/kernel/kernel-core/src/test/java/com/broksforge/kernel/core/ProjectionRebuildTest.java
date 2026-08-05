package com.broksforge.kernel.core;

import com.broksforge.kernel.api.Address;
import com.broksforge.kernel.api.Name;
import com.broksforge.kernel.core.command.AppendCommand;
import com.broksforge.kernel.core.engine.ForgeKernel;
import com.broksforge.kernel.core.log.LogEntry;
import com.broksforge.kernel.core.log.Payload;
import com.broksforge.kernel.core.memory.InMemoryGraphIndex;
import com.broksforge.kernel.core.memory.InMemoryNameStore;
import com.broksforge.kernel.core.memory.InMemoryRevisionStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ADR-V2-0001: the log is the sole source of truth and every projection is rebuildable from it.
 * This replays a kernel's log into fresh, empty projections and asserts they answer correctly —
 * proving no projection holds any truth the log does not.
 */
class ProjectionRebuildTest {

    @Test
    @DisplayName("fresh projections rebuilt from the log alone reproduce kernel state")
    void rebuildFromLog() {
        ForgeKernel k = Fixtures.kernel();
        Address.Revision v1 = (Address.Revision) k.append(
                Fixtures.ORG, new AppendCommand.CreateNode(Fixtures.prompt("v1")), Fixtures.ACTOR)
                .address().orElseThrow();
        Address.Revision v2 = (Address.Revision) k.append(
                Fixtures.ORG, new AppendCommand.AddRevision(v1.node(), Fixtures.prompt("v2")), Fixtures.ACTOR)
                .address().orElseThrow();
        Name prod = Name.of("prod");
        k.append(Fixtures.ORG, new AppendCommand.RepointName(prod, v2, null), Fixtures.ACTOR);

        // Rebuild every projection from nothing but the log.
        InMemoryRevisionStore revisions = new InMemoryRevisionStore();
        InMemoryGraphIndex graph = new InMemoryGraphIndex();
        InMemoryNameStore names = new InMemoryNameStore();
        for (LogEntry entry : k.log(Fixtures.ORG)) {
            if (entry.payload() instanceof Payload.NodePut np) {
                revisions.put(np.revision().hash(), np.revision());
            }
            graph.apply(entry);
            names.apply(entry);
        }

        assertTrue(revisions.contains(v1.revision()));
        assertTrue(revisions.contains(v2.revision()));
        assertEquals(2, graph.revisionsOf(Fixtures.ORG, v1.node()).size());
        assertEquals(Optional.of(v2), names.current(Fixtures.ORG, prod));
        assertTrue(k.verifyChain(Fixtures.ORG));
    }
}
