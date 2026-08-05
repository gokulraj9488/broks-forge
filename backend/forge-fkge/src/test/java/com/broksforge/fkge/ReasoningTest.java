package com.broksforge.fkge;

import com.broksforge.fkge.explain.Explanation;
import com.broksforge.fkge.explain.LeafKind;
import com.broksforge.fkge.reason.CausalTrace;
import com.broksforge.fkge.reason.ConfidenceResult;
import com.broksforge.knowledge.graph.KnowledgeGraph;
import com.broksforge.knowledge.graph.KnowledgeObject;
import com.broksforge.knowledge.ontology.ObjectTypes;
import com.broksforge.knowledge.ontology.Verbs;
import com.broksforge.kernel.api.canonical.CanonicalValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Confidence propagation (min bound), causal tracing (log-position soundness), and honest explanation. */
class ReasoningTest {

    @Test
    @DisplayName("confidence propagates as the weakest supporting claim (min), not a product")
    void confidenceIsWeakestLink() {
        TestSupport.Scenario s = TestSupport.scenario();
        KnowledgeGraphEngine fkge = KnowledgeGraphEngine.open(s.repo);
        ConfidenceResult c = fkge.confidenceOf(s.deployment);
        assertTrue(c.defined());
        assertEquals(0, c.confidence().compareTo(new BigDecimal("0.60")), "min(0.90, 0.60) = 0.60");
        assertEquals(s.benchmark, c.weakestLink());
    }

    @Test
    @DisplayName("observations are certain (1); artifacts without claims are not truth-bearers (undefined)")
    void confidenceEdgeCases() {
        TestSupport.Scenario s = TestSupport.scenario();
        KnowledgeGraphEngine fkge = KnowledgeGraphEngine.open(s.repo);
        assertEquals(0, fkge.confidenceOf(s.run).confidence().compareTo(BigDecimal.ONE));
        assertFalse(fkge.confidenceOf(s.provider).defined());
    }

    @Test
    @DisplayName("root cause of the incident is the deployment, and the trace is causally sound")
    void rootCauseSound() {
        TestSupport.Scenario s = TestSupport.scenario();
        KnowledgeGraphEngine fkge = KnowledgeGraphEngine.open(s.repo);
        CausalTrace trace = fkge.rootCause(s.incident);
        assertTrue(trace.causes().stream().anyMatch(g -> g.id().equals(s.deployment)));
        assertTrue(trace.sound(), "the cause precedes its effect in log position");
    }

    @Test
    @DisplayName("explaining the deployment bottoms out at axioms: complete, with an observation leaf")
    void explanationIsComplete() {
        TestSupport.Scenario s = TestSupport.scenario();
        KnowledgeGraphEngine fkge = KnowledgeGraphEngine.open(s.repo);
        Explanation e = fkge.whyApproved(s.deployment);
        assertTrue(e.complete(), () -> "expected a complete proof tree; gaps=" + e.gaps());
        assertTrue(e.leaves().stream().anyMatch(l -> l.kind() == LeafKind.OBSERVATION));
        assertTrue(e.leaves().stream().anyMatch(l -> l.node().equals(s.run)));
    }

    @Test
    @DisplayName("honesty: a grounded decision seen through a lens that hides its grounding is a gap, not 'complete'")
    void noSilentCompleteness() {
        TestSupport.Scenario s = TestSupport.scenario();
        KnowledgeGraphEngine fkge = KnowledgeGraphEngine.open(s.repo);
        // The deployment rests_on claims (intent), but the EVIDENCE-only lens cannot follow intent.
        Explanation e = fkge.explain(s.deployment, com.broksforge.fkge.query.Lenses.EVIDENCE);
        assertFalse(e.complete(), "must not certify a decision whose grounding this lens never followed");
        assertFalse(e.gaps().isEmpty(), "the gap must be named, not hidden");
    }

    @Test
    @DisplayName("a decision reached as a leaf is classified a judgment-call")
    void judgmentCallLeaf() {
        var repo = TestSupport.repo();
        KnowledgeGraph kg = repo.knowledge();
        // A pure judgment-call approval, and a second approval that approves it.
        KnowledgeObject decided = kg.define(ObjectTypes.APPROVAL,
                CanonicalValue.objectBuilder().put("judgment-call", true).build());
        KnowledgeObject approver = kg.define(ObjectTypes.APPROVAL,
                CanonicalValue.objectBuilder().put("judgment-call", true).build());
        kg.relate(approver, Verbs.APPROVES, decided); // intent-family edge approver -> decided

        KnowledgeGraphEngine fkge = KnowledgeGraphEngine.open(repo);
        Explanation e = fkge.explain(approver.node());
        assertTrue(e.leaves().stream().anyMatch(l -> l.kind() == LeafKind.JUDGMENT_CALL && l.node().equals(decided.node())));
    }
}
