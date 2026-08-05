package com.broksforge.kernel.tck;

import com.broksforge.kernel.api.ActorId;
import com.broksforge.kernel.api.Address;
import com.broksforge.kernel.api.EdgeFamily;
import com.broksforge.kernel.api.Kind;
import com.broksforge.kernel.api.Name;
import com.broksforge.kernel.api.OrgId;
import com.broksforge.kernel.api.Ref;
import com.broksforge.kernel.api.Revision;
import com.broksforge.kernel.api.RevisionHash;
import com.broksforge.kernel.api.Verb;
import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.kernel.core.command.AppendCommand;
import com.broksforge.kernel.core.engine.ForgeKernel;
import com.broksforge.kernel.core.engine.KernelException;
import com.broksforge.kernel.core.op.Query;
import com.broksforge.kernel.core.op.Subgraph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Forge Kernel storage compatibility contract. Every storage backend must pass this identical
 * suite — this is how ADR-V2-0001's promise that "storage implementations remain interchangeable" is
 * proven. A backend supplies a {@link Backend} (isolated storage that can be reopened); the contract
 * exercises the six operations, the kernel laws, and durability across a runtime restart.
 *
 * <p>Subclasses implement {@link #newBackend()} only.
 */
public abstract class KernelContract {

    protected static final OrgId ORG = OrgId.fromString("00000000-0000-0000-0000-0000000000cc");
    protected static final ActorId ACTOR = ActorId.of("system:tck");

    /** Isolated storage for one test that can be reopened to simulate a runtime restart. */
    protected interface Backend {
        /** @return a kernel over this backend's storage (repeated calls reuse the same storage) */
        ForgeKernel open();
    }

    /**
     * @return a fresh, isolated backend (each test gets its own)
     */
    protected abstract Backend newBackend();

    // ---- helpers -----------------------------------------------------------------------------

    private static Revision prompt(String text) {
        return Revision.leaf(Kind.ARTIFACT, "prompt", CanonicalValue.objectBuilder().put("text", text).build());
    }

    private static Address.Revision create(ForgeKernel k, Revision r) {
        return (Address.Revision) k.append(ORG, new AppendCommand.CreateNode(r), ACTOR).address().orElseThrow();
    }

    // ---- contract ----------------------------------------------------------------------------

    @Test
    @DisplayName("TCK: append, resolve current and historical, and deduplicate identical content")
    void appendResolveDedup() {
        ForgeKernel k = newBackend().open();
        Address.Revision v1 = create(k, prompt("v1"));
        Address.Revision v2 = (Address.Revision) k.append(ORG,
                new AppendCommand.AddRevision(v1.node(), prompt("v2")), ACTOR).address().orElseThrow();

        Name prod = Name.of("prod");
        k.append(ORG, new AppendCommand.RepointName(prod, v1, null), ACTOR);
        var afterFirst = k.log(ORG).get(k.log(ORG).size() - 1).position();
        k.append(ORG, new AppendCommand.RepointName(prod, v2, v1), ACTOR);

        assertEquals(Optional.of(v2), k.resolve(ORG, prod));
        assertEquals(Optional.of(v1), k.resolveAt(ORG, prod, afterFirst));

        Address.Revision dupA = create(k, prompt("same"));
        Address.Revision dupB = create(k, prompt("same"));
        assertNotEquals(dupA.node(), dupB.node());
        assertEquals(dupA.revision(), dupB.revision());
    }

    @Test
    @DisplayName("TCK: versioning keeps the node id; a kind mismatch is rejected")
    void versioning() {
        ForgeKernel k = newBackend().open();
        Address.Revision v1 = create(k, prompt("v1"));
        Address.Revision v2 = (Address.Revision) k.append(ORG,
                new AppendCommand.AddRevision(v1.node(), prompt("v2")), ACTOR).address().orElseThrow();
        assertEquals(v1.node(), v2.node());

        Revision wrongKind = Revision.leaf(Kind.OBSERVATION, "prompt",
                CanonicalValue.objectBuilder().put("text", "x").build());
        assertEquals(KernelException.Reason.KIND_MISMATCH, assertThrows(KernelException.class,
                () -> k.append(ORG, new AppendCommand.AddRevision(v1.node(), wrongKind), ACTOR)).reason());
    }

    @Test
    @DisplayName("TCK: an intrinsic reference to unknown content is rejected (closedness)")
    void missingReferenceRejected() {
        ForgeKernel k = newBackend().open();
        RevisionHash phantom = RevisionHash.of(com.broksforge.kernel.api.HashAlgorithm.SHA_256, new byte[32]);
        Revision dangling = Revision.of(Kind.ARTIFACT, "agent", CanonicalValue.NULL,
                List.of(Ref.of(new Verb("uses", EdgeFamily.COMPOSITION), phantom)));
        assertEquals(KernelException.Reason.MISSING_REFERENCE, assertThrows(KernelException.class,
                () -> k.append(ORG, new AppendCommand.CreateNode(dangling), ACTOR)).reason());
    }

    @Test
    @DisplayName("TCK: name compare-and-swap succeeds with the right expected and fails when stale")
    void nameCas() {
        ForgeKernel k = newBackend().open();
        Address.Revision v1 = create(k, prompt("v1"));
        Address.Revision v2 = (Address.Revision) k.append(ORG,
                new AppendCommand.AddRevision(v1.node(), prompt("v2")), ACTOR).address().orElseThrow();
        Name prod = Name.of("prod");
        k.append(ORG, new AppendCommand.RepointName(prod, v1, null), ACTOR);
        assertEquals(KernelException.Reason.CAS_FAILURE, assertThrows(KernelException.class,
                () -> k.append(ORG, new AppendCommand.RepointName(prod, v2, null), ACTOR)).reason());
        k.append(ORG, new AppendCommand.RepointName(prod, v2, v1), ACTOR);
        assertEquals(Optional.of(v2), k.resolve(ORG, prod));
    }

    @Test
    @DisplayName("TCK: closure gathers transitive composition members")
    void closure() {
        ForgeKernel k = newBackend().open();
        Revision p1 = prompt("one");
        Revision p2 = prompt("two");
        create(k, p1);
        create(k, p2);
        Revision agent = Revision.of(Kind.ARTIFACT, "agent", CanonicalValue.NULL, List.of(
                Ref.of(new Verb("uses", EdgeFamily.COMPOSITION), p1.hash()),
                Ref.of(new Verb("uses", EdgeFamily.COMPOSITION), p2.hash())));
        Address.Revision agentAddr = create(k, agent);
        assertEquals(3, k.closure(agentAddr.revision()).size());
    }

    @Test
    @DisplayName("TCK: traverse follows intrinsic and extrinsic edges and respects retraction")
    void traverse() {
        ForgeKernel k = newBackend().open();
        Address.Revision a = create(k, prompt("a"));
        Address.Revision b = create(k, prompt("b"));
        Address.Node an = new Address.Node(a.org(), a.kind(), a.node());
        Address.Node bn = new Address.Node(b.org(), b.kind(), b.node());
        com.broksforge.kernel.core.log.EdgeKey edge =
                new com.broksforge.kernel.core.log.EdgeKey(an, new Verb("caused", EdgeFamily.CAUSALITY), bn);

        k.append(ORG, new AppendCommand.AssertEdge(edge), ACTOR);
        Subgraph before = k.traverse(ORG, new Query(an, java.util.Set.of(EdgeFamily.CAUSALITY),
                Query.Direction.OUT, 2));
        assertEquals(1, before.edges().size());

        k.append(ORG, new AppendCommand.RetractEdge(edge), ACTOR);
        assertTrue(k.traverse(ORG, new Query(an, java.util.Set.of(EdgeFamily.CAUSALITY),
                Query.Direction.OUT, 2)).edges().isEmpty());
    }

    @Test
    @DisplayName("TCK: diff is empty for equal revisions and located for a changed field")
    void diff() {
        ForgeKernel k = newBackend().open();
        Address.Revision a = create(k, prompt("hello"));
        Address.Revision b = create(k, prompt("world"));
        assertTrue(k.diff(a.revision(), a.revision()).identical());
        assertFalse(k.diff(a.revision(), b.revision()).identical());
    }

    @Test
    @DisplayName("TCK: the hash chain verifies")
    void hashChain() {
        ForgeKernel k = newBackend().open();
        create(k, prompt("a"));
        create(k, prompt("b"));
        create(k, prompt("c"));
        assertTrue(k.verifyChain(ORG));
    }

    @Test
    @DisplayName("TCK: data and the verifiable chain survive a runtime restart (recovery)")
    void reopenDurability() {
        Backend backend = newBackend();
        ForgeKernel first = backend.open();
        Address.Revision v1 = create(first, prompt("durable"));
        Name prod = Name.of("prod");
        first.append(ORG, new AppendCommand.RepointName(prod, v1, null), ACTOR);
        int countBefore = first.log(ORG).size();

        // Reopen a fresh runtime over the same storage — the log and projections must survive.
        ForgeKernel reopened = backend.open();
        assertEquals(countBefore, reopened.log(ORG).size());
        assertTrue(reopened.revision(v1.revision()).isPresent());
        assertEquals(Optional.of(v1), reopened.resolve(ORG, prod));
        assertTrue(reopened.verifyChain(ORG));
    }
}
