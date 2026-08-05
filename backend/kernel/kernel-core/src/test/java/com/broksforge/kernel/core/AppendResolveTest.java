package com.broksforge.kernel.core;

import com.broksforge.kernel.api.Address;
import com.broksforge.kernel.api.LogPosition;
import com.broksforge.kernel.api.Name;
import com.broksforge.kernel.api.Revision;
import com.broksforge.kernel.core.command.AppendCommand;
import com.broksforge.kernel.core.command.AppendResult;
import com.broksforge.kernel.core.engine.ForgeKernel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** append, resolve, versioning, and name repointing (with historical resolution). */
class AppendResolveTest {

    @Test
    @DisplayName("create node returns a revision address and records the fact")
    void createNode() {
        ForgeKernel k = Fixtures.kernel();
        AppendResult r = k.append(Fixtures.ORG, new AppendCommand.CreateNode(Fixtures.prompt("hi")), Fixtures.ACTOR);
        Address.Revision addr = (Address.Revision) r.address().orElseThrow();
        assertEquals(1L, r.entry().position().value());
        assertEquals(Fixtures.prompt("hi").hash(), addr.revision());
        assertTrue(k.revision(addr.revision()).isPresent());
    }

    @Test
    @DisplayName("add revision versions an existing continuant under the same node id")
    void addRevision() {
        ForgeKernel k = Fixtures.kernel();
        Address.Revision v1 = (Address.Revision) k.append(
                Fixtures.ORG, new AppendCommand.CreateNode(Fixtures.prompt("v1")), Fixtures.ACTOR)
                .address().orElseThrow();
        Address.Revision v2 = (Address.Revision) k.append(
                Fixtures.ORG, new AppendCommand.AddRevision(v1.node(), Fixtures.prompt("v2")), Fixtures.ACTOR)
                .address().orElseThrow();
        assertEquals(v1.node(), v2.node());               // same continuant
        assertNotEquals(v1.revision(), v2.revision());     // different state
    }

    @Test
    @DisplayName("repoint name, resolve current, and resolve historically")
    void namesAndHistory() {
        ForgeKernel k = Fixtures.kernel();
        Address.Revision v1 = (Address.Revision) k.append(
                Fixtures.ORG, new AppendCommand.CreateNode(Fixtures.prompt("v1")), Fixtures.ACTOR)
                .address().orElseThrow();
        Address.Revision v2 = (Address.Revision) k.append(
                Fixtures.ORG, new AppendCommand.AddRevision(v1.node(), Fixtures.prompt("v2")), Fixtures.ACTOR)
                .address().orElseThrow();

        Name prod = Name.of("prod");
        k.append(Fixtures.ORG, new AppendCommand.RepointName(prod, v1, null), Fixtures.ACTOR);
        LogPosition afterFirstDeploy = new LogPosition(3);
        k.append(Fixtures.ORG, new AppendCommand.RepointName(prod, v2, v1), Fixtures.ACTOR);

        assertEquals(Optional.of(v2), k.resolve(Fixtures.ORG, prod));                 // current
        assertEquals(Optional.of(v1), k.resolveAt(Fixtures.ORG, prod, afterFirstDeploy)); // rollback view
        assertEquals(Optional.empty(), k.resolveAt(Fixtures.ORG, prod, new LogPosition(2))); // before any deploy
    }

    @Test
    @DisplayName("identical content deduplicates to one revision across two continuants")
    void dedup() {
        ForgeKernel k = Fixtures.kernel();
        Revision same = Fixtures.prompt("same");
        Address.Revision a = (Address.Revision) k.append(
                Fixtures.ORG, new AppendCommand.CreateNode(same), Fixtures.ACTOR).address().orElseThrow();
        Address.Revision b = (Address.Revision) k.append(
                Fixtures.ORG, new AppendCommand.CreateNode(same), Fixtures.ACTOR).address().orElseThrow();
        assertNotEquals(a.node(), b.node());          // two continuants (two facts)
        assertEquals(a.revision(), b.revision());     // one revision (dedup)
        assertSame(k.revision(a.revision()).orElseThrow(), k.revision(b.revision()).orElseThrow());
    }
}
