package com.broksforge.kernel.core;

import com.broksforge.kernel.api.Address;
import com.broksforge.kernel.api.EdgeFamily;
import com.broksforge.kernel.api.Kind;
import com.broksforge.kernel.api.Name;
import com.broksforge.kernel.api.NodeId;
import com.broksforge.kernel.api.Ref;
import com.broksforge.kernel.api.Revision;
import com.broksforge.kernel.api.RevisionHash;
import com.broksforge.kernel.api.Verb;
import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.kernel.core.command.AppendCommand;
import com.broksforge.kernel.core.engine.ForgeKernel;
import com.broksforge.kernel.core.engine.KernelException;
import com.broksforge.kernel.core.log.LogEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every kernel law that can only be enforced at append time gets an explicit test here. */
class LawEnforcementTest {

    @Test
    @DisplayName("Law 3 chain: verifyChain holds and each entry links to the previous")
    void hashChain() {
        ForgeKernel k = Fixtures.kernel();
        k.append(Fixtures.ORG, new AppendCommand.CreateNode(Fixtures.prompt("a")), Fixtures.ACTOR);
        k.append(Fixtures.ORG, new AppendCommand.CreateNode(Fixtures.prompt("b")), Fixtures.ACTOR);
        k.append(Fixtures.ORG, new AppendCommand.CreateNode(Fixtures.prompt("c")), Fixtures.ACTOR);

        assertTrue(k.verifyChain(Fixtures.ORG));
        List<LogEntry> entries = k.log(Fixtures.ORG);
        assertEquals(3, entries.size());
        assertTrue(entries.get(0).prevHash() == null);
        assertEquals(entries.get(0).entryHash(), entries.get(1).prevHash());
        assertEquals(entries.get(1).entryHash(), entries.get(2).prevHash());
        for (LogEntry e : entries) {
            assertTrue(e.verifySelf());
        }
    }

    @Test
    @DisplayName("a tampered entry fails self-verification")
    void tamperDetected() {
        ForgeKernel k = Fixtures.kernel();
        LogEntry good = k.append(Fixtures.ORG, new AppendCommand.CreateNode(Fixtures.prompt("x")), Fixtures.ACTOR)
                .entry();
        // Forge a mismatch between content and stored hash: swap in a different entry hash.
        RevisionHash wrong = com.broksforge.kernel.api.canonical.ContentHash.of(
                CanonicalValue.of("not the real entry"));
        LogEntry tampered = new LogEntry(good.org(), good.position(), good.prevHash(),
                good.provenance(), good.payload(), wrong);
        assertFalse(tampered.verifySelf());
        assertTrue(good.verifySelf());
    }

    @Test
    @DisplayName("Law 5/7 closedness: an intrinsic reference to unknown content is rejected")
    void missingReferenceRejected() {
        ForgeKernel k = Fixtures.kernel();
        RevisionHash phantom = RevisionHash.of(
                com.broksforge.kernel.api.HashAlgorithm.SHA_256, new byte[32]);
        Revision danglingRef = Fixtures.artifact("agent", CanonicalValue.NULL,
                List.of(Ref.of(new Verb("uses", EdgeFamily.COMPOSITION), phantom)));
        KernelException ex = assertThrows(KernelException.class,
                () -> k.append(Fixtures.ORG, new AppendCommand.CreateNode(danglingRef), Fixtures.ACTOR));
        assertEquals(KernelException.Reason.MISSING_REFERENCE, ex.reason());
    }

    @Test
    @DisplayName("adding a revision to an unknown node, or with a mismatched kind, is rejected")
    void nodeAndKindChecks() {
        ForgeKernel k = Fixtures.kernel();
        NodeId ghost = new NodeId(UUID.fromString("00000000-0000-0000-0000-0000000000ff"));
        assertEquals(KernelException.Reason.UNKNOWN_NODE, assertThrows(KernelException.class,
                () -> k.append(Fixtures.ORG, new AppendCommand.AddRevision(ghost, Fixtures.prompt("x")), Fixtures.ACTOR))
                .reason());

        Address.Revision v1 = (Address.Revision) k.append(
                Fixtures.ORG, new AppendCommand.CreateNode(Fixtures.prompt("v1")), Fixtures.ACTOR)
                .address().orElseThrow();
        Revision wrongKind = Revision.leaf(Kind.OBSERVATION, "prompt",
                CanonicalValue.objectBuilder().put("text", "v2").build());
        assertEquals(KernelException.Reason.KIND_MISMATCH, assertThrows(KernelException.class,
                () -> k.append(Fixtures.ORG, new AppendCommand.AddRevision(v1.node(), wrongKind), Fixtures.ACTOR))
                .reason());
    }

    @Test
    @DisplayName("ADR-V2-0006 compare-and-swap: a stale expected target loses")
    void nameCas() {
        ForgeKernel k = Fixtures.kernel();
        Address.Revision v1 = (Address.Revision) k.append(
                Fixtures.ORG, new AppendCommand.CreateNode(Fixtures.prompt("v1")), Fixtures.ACTOR)
                .address().orElseThrow();
        Address.Revision v2 = (Address.Revision) k.append(
                Fixtures.ORG, new AppendCommand.AddRevision(v1.node(), Fixtures.prompt("v2")), Fixtures.ACTOR)
                .address().orElseThrow();
        Name prod = Name.of("prod");

        k.append(Fixtures.ORG, new AppendCommand.RepointName(prod, v1, null), Fixtures.ACTOR);
        // Expecting "new" again is stale — the name already exists.
        assertEquals(KernelException.Reason.CAS_FAILURE, assertThrows(KernelException.class,
                () -> k.append(Fixtures.ORG, new AppendCommand.RepointName(prod, v2, null), Fixtures.ACTOR))
                .reason());
        // Correct expected succeeds.
        k.append(Fixtures.ORG, new AppendCommand.RepointName(prod, v2, v1), Fixtures.ACTOR);
        assertEquals(java.util.Optional.of(v2), k.resolve(Fixtures.ORG, prod));
    }

    @Test
    @DisplayName("repointing a name at a nonexistent revision is rejected")
    void nameTargetMustExist() {
        ForgeKernel k = Fixtures.kernel();
        RevisionHash phantom = RevisionHash.of(com.broksforge.kernel.api.HashAlgorithm.SHA_256, new byte[32]);
        Address.Revision bogus = new Address.Revision(
                Fixtures.ORG, Kind.ARTIFACT, new NodeId(UUID.randomUUID()), phantom);
        assertEquals(KernelException.Reason.MISSING_TARGET, assertThrows(KernelException.class,
                () -> k.append(Fixtures.ORG, new AppendCommand.RepointName(Name.of("prod"), bogus, null), Fixtures.ACTOR))
                .reason());
    }
}
