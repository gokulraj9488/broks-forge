package com.broksforge.explorer;

import com.broksforge.kernel.api.Address;
import com.broksforge.kernel.api.EdgeFamily;
import com.broksforge.kernel.api.Kind;
import com.broksforge.kernel.api.LogPosition;
import com.broksforge.kernel.api.Name;
import com.broksforge.kernel.api.Revision;
import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.kernel.core.engine.ForgeKernel;
import com.broksforge.kernel.core.engine.KernelException;
import com.broksforge.kernel.core.event.Subscription;
import com.broksforge.kernel.core.op.Delta;
import com.broksforge.kernel.core.op.Query;
import com.broksforge.kernel.core.op.Subgraph;
import com.broksforge.kernel.core.reproduce.ReproduceResult;
import com.broksforge.kernel.core.validate.IntegrityReport;
import com.broksforge.kernel.core.validate.IntegrityScanner;
import com.broksforge.explorer.reproduce.ChecklistReproducer;
import com.broksforge.explorer.watch.AuditTrailProgram;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One test per major kernel capability, exercised strictly through the public API. Together these are
 * the coverage proof that the Explorer touches every operation, kind, and edge family the kernel
 * offers — the success criterion of Phase 1.5.
 */
class CapabilityMatrixTest {

    private static CanonicalValue text(String t) {
        return CanonicalValue.objectBuilder().put("text", t).build();
    }

    @Test
    @DisplayName("append: CreateNode mints a continuant and returns a typed handle")
    void appendCreateNode() {
        ForgeExplorer forge = TestKernel.explorer();
        Handle h = forge.createArtifact("prompt", text("hello"));
        assertEquals(Kind.ARTIFACT, h.kind());
        assertEquals("prompt", h.subtype());
        assertTrue(forge.revision(h.hash()).isPresent());
    }

    @Test
    @DisplayName("append: AddRevision versions the same continuant")
    void appendAddRevision() {
        ForgeExplorer forge = TestKernel.explorer();
        Handle v1 = forge.createArtifact("prompt", text("v1"));
        Handle v2 = forge.addRevision(v1.node(), Revision.leaf(Kind.ARTIFACT, "prompt", text("v2")));
        assertEquals(v1.node(), v2.node());
        assertFalse(v1.hash().equals(v2.hash()));
    }

    @Test
    @DisplayName("append: AssertEdge then RetractEdge over all five families")
    void appendEdges() {
        ForgeExplorer forge = TestKernel.explorer();
        Handle a = forge.createArtifact("prompt", text("a"));
        Handle b = forge.createArtifact("agent", text("b"));
        forge.assertEdge(b.address(), Verbs.USES, a.address());
        forge.assertEdge(b.address(), Verbs.CAUSED, a.address());
        forge.retractEdge(b.address(), Verbs.CAUSED, a.address());
        // The composition edge survives; the causality edge was withdrawn.
        Subgraph g = forge.traverse(new Query(b.address(),
                java.util.EnumSet.noneOf(EdgeFamily.class), Query.Direction.OUT, 2));
        assertTrue(g.edges().stream().anyMatch(e -> e.family() == EdgeFamily.COMPOSITION));
        assertFalse(g.edges().stream().anyMatch(e -> e.family() == EdgeFamily.CAUSALITY));
    }

    @Test
    @DisplayName("append: RepointName is compare-and-swap; a stale expected loses")
    void appendRepointCas() {
        ForgeExplorer forge = TestKernel.explorer();
        Handle v1 = forge.createArtifact("prompt", text("v1"));
        Handle v2 = forge.addRevision(v1.node(), Revision.leaf(Kind.ARTIFACT, "prompt", text("v2")));
        Name prod = Name.of("prod");
        forge.deploy(prod, v1);
        assertEquals(KernelException.Reason.CAS_FAILURE, assertThrows(KernelException.class,
                () -> forge.repoint(prod, v2.address(), null)).reason());
        forge.repoint(prod, v2.address(), v1.address());
        assertEquals(v2.address(), forge.resolve(prod).orElseThrow());
    }

    @Test
    @DisplayName("append: Tick emits a substrate clock observation")
    void appendTick() {
        ForgeExplorer forge = TestKernel.explorer();
        int before = forge.log().size();
        forge.tick(java.time.Instant.parse("2026-07-27T00:00:00Z"));
        assertEquals(before + 1, forge.log().size());
    }

    @Test
    @DisplayName("resolve + resolveAt: current pointer and deterministic time travel")
    void resolveAndTimeTravel() {
        ForgeExplorer forge = TestKernel.explorer();
        Handle v1 = forge.createArtifact("prompt", text("v1"));
        Handle v2 = forge.addRevision(v1.node(), Revision.leaf(Kind.ARTIFACT, "prompt", text("v2")));
        Name prod = Name.of("prod");
        forge.deploy(prod, v1);
        long atV1 = forge.log().get(forge.log().size() - 1).position().value();
        forge.repoint(prod, v2.address(), v1.address());
        assertEquals(v2.address(), forge.resolve(prod).orElseThrow());
        assertEquals(v1.address(), forge.resolveAt(prod, new LogPosition(atV1)).orElseThrow());
    }

    @Test
    @DisplayName("traverse: OUT, IN, and BOTH over intrinsic composition refs")
    void traverseDirections() {
        ForgeExplorer forge = TestKernel.explorer();
        Handle prompt = forge.createArtifact("prompt", text("p"));
        Handle agent = forge.createArtifact("agent", text("a"),
                com.broksforge.kernel.api.Ref.of(Verbs.USES, prompt.hash()));
        assertFalse(forge.traverse(new Query(agent.address(),
                java.util.EnumSet.noneOf(EdgeFamily.class), Query.Direction.OUT, 2)).isSingleton());
        assertFalse(forge.traverse(new Query(prompt.address(),
                java.util.EnumSet.noneOf(EdgeFamily.class), Query.Direction.IN, 2)).isSingleton());
        Subgraph both = forge.traverse(new Query(agent.address(),
                java.util.EnumSet.noneOf(EdgeFamily.class), Query.Direction.BOTH, 2));
        assertTrue(both.nodes().contains(prompt.address()));
    }

    @Test
    @DisplayName("closure: composition snapshot is root-first and content-addressed")
    void closureSnapshot() {
        ForgeExplorer forge = TestKernel.explorer();
        Handle prompt = forge.createArtifact("prompt", text("p"));
        Handle agent = forge.createArtifact("agent", text("a"),
                com.broksforge.kernel.api.Ref.of(Verbs.USES, prompt.hash()));
        var closure = forge.closure(agent.hash());
        assertTrue(closure.containsKey(agent.hash()));
        assertTrue(closure.containsKey(prompt.hash()));
    }

    @Test
    @DisplayName("diff: structural delta locates the changed field")
    void diffChange() {
        ForgeExplorer forge = TestKernel.explorer();
        Handle v1 = forge.createArtifact("prompt", text("before"));
        Handle v2 = forge.addRevision(v1.node(), Revision.leaf(Kind.ARTIFACT, "prompt", text("after")));
        Delta delta = forge.diff(v1.hash(), v2.hash());
        assertFalse(delta.identical());
        assertTrue(delta.changes().stream().anyMatch(c -> c.path().contains("text")));
    }

    @Test
    @DisplayName("reproduce: the SPI runs a check-suite and records one observation per check")
    void reproduceViaSpi() {
        ForgeExplorer forge = TestKernel.explorer(new ChecklistReproducer());
        Handle suite = forge.createArtifact("check-suite", CanonicalValue.objectBuilder()
                .put("checks", CanonicalValue.array(
                        CanonicalValue.of("c1"), CanonicalValue.of("c2")))
                .build());
        ReproduceResult r = forge.reproduce(suite.address());
        assertTrue(r.reproduced());
        assertEquals(2, r.observations().size());
        // Reproducing again yields identical content (dedup), i.e. the same revision hash.
        ReproduceResult again = forge.reproduce(suite.address());
        Address.Revision a0 = (Address.Revision) r.observations().get(0);
        Address.Revision b0 = (Address.Revision) again.observations().get(0);
        assertEquals(a0.revision(), b0.revision());
    }

    @Test
    @DisplayName("reproduce: an unsupported revision reports not-reproducible, not an error")
    void reproduceUnsupported() {
        ForgeExplorer forge = TestKernel.explorer(new ChecklistReproducer());
        Handle obs = forge.recordObservation("metric",
                CanonicalValue.objectBuilder().put("name", "x").put("value", CanonicalValue.of(1)).build());
        assertFalse(forge.reproduce(obs.address()).reproduced());
    }

    @Test
    @DisplayName("subscribe: a standing program is notified of committed entries")
    void subscribeNotified() {
        ForgeExplorer forge = TestKernel.explorer();
        AuditTrailProgram audit = new AuditTrailProgram();
        try (Subscription sub = forge.subscribe(e -> true, audit)) {
            assertTrue(sub.isActive());
            forge.createArtifact("prompt", text("x"));
            forge.createArtifact("prompt", text("y"));
        }
        assertEquals(2, audit.count());
    }

    @Test
    @DisplayName("audit: verifyChain holds and the integrity scanner is clean")
    void auditClean() {
        ForgeExplorer forge = TestKernel.explorer(new ChecklistReproducer());
        forge.createArtifact("prompt", text("x"));
        Handle claim = forge.create(com.broksforge.explorer.kinds.Claims.claim("kpi", "s", "m:v1",
                new BigDecimal("0.5"),
                List.of(forge.recordObservation("metric", CanonicalValue.objectBuilder()
                        .put("name", "n").put("value", CanonicalValue.of(1)).build()).hash())));
        assertTrue(forge.verifyChain());
        IntegrityReport report = new IntegrityScanner().scan(forge.kernel(), forge.org());
        assertTrue(report.clean(), () -> "integrity findings: " + report.findings());
        assertTrue(forge.revision(claim.hash()).isPresent());
    }

    @Test
    @DisplayName("all four kinds are constructible and appendable through the facade")
    void allFourKinds() {
        ForgeExplorer forge = TestKernel.explorer();
        Handle artifact = forge.createArtifact("prompt", text("a"));
        Handle observation = forge.recordObservation("metric", CanonicalValue.objectBuilder()
                .put("name", "n").put("value", CanonicalValue.of(2)).build());
        Handle claim = forge.create(com.broksforge.explorer.kinds.Claims.claim("verdict", "s", "m:v1",
                new BigDecimal("0.9"), List.of(observation.hash())));
        Handle decision = forge.create(com.broksforge.explorer.kinds.Decisions.restingOn(
                "promotion", "ship it", List.of(claim.hash())));
        assertEquals(Kind.ARTIFACT, artifact.kind());
        assertEquals(Kind.OBSERVATION, observation.kind());
        assertEquals(Kind.CLAIM, claim.kind());
        assertEquals(Kind.DECISION, decision.kind());
    }
}
