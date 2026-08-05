package com.broksforge.fxp;

import com.broksforge.fvcs.diff.ChangeKind;
import com.broksforge.fvcs.repo.Branch;
import com.broksforge.fvcs.repo.CommitRef;
import com.broksforge.fxp.review.ClaimReview;
import com.broksforge.fxp.review.CommitReview;
import com.broksforge.fxp.review.DecisionReview;
import com.broksforge.fxp.studio.StudioService;
import com.broksforge.knowledge.graph.KnowledgeObject;
import com.broksforge.knowledge.ontology.ObjectTypes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Review reads change and evidence through the platform and records verdicts as first-class decisions. */
class ReviewTest {

    @Test
    @DisplayName("reviewing a commit shows the semantic diff and each change's blast radius")
    void reviewCommitShowsChangeAndImpact() {
        FxpTestSupport.Scenario s = FxpTestSupport.scenario();
        StudioService studio = s.client.studio();
        Branch main = studio.branch("main");
        CommitRef c1 = studio.commit(main, studio.snapshot("v1", List.of(s.provider, s.model, s.prompt)), "v1");
        KnowledgeObject prompt2 = studio.revise(s.prompt, FxpTestSupport.obj("text", "you are a concise assistant"));
        CommitRef c2 = studio.commit(main, studio.snapshot("v2", List.of(s.provider, s.model, prompt2)), "tighten tone");

        CommitReview review = s.client.review().reviewCommit(c1, c2);
        assertFalse(review.clean());
        assertEquals(1, review.changes().of(ChangeKind.CHANGED).size());
        assertTrue(review.impacts().stream().anyMatch(i -> i.node().equals(s.prompt.node())));
    }

    @Test
    @DisplayName("reviewing a decision returns its proof and confidence; reviewing a claim returns its evidence")
    void reviewDecisionAndClaim() {
        FxpTestSupport.Scenario s = FxpTestSupport.scenario();
        DecisionReview dr = s.client.review().reviewDecision(s.deployment.node());
        assertTrue(dr.justified());
        ClaimReview cr = s.client.review().reviewClaim(s.verdict.node());
        assertTrue(cr.grounded());
    }

    @Test
    @DisplayName("approval is recorded as a first-class Approval decision (the AI-PR triad)")
    void approvalIsAFact() {
        FxpTestSupport.Scenario s = FxpTestSupport.scenario();
        KnowledgeObject approval = s.client.review().approve(s.deployment, "LGTM: evidence is sufficient");
        assertEquals(ObjectTypes.APPROVAL, approval.type());
        // the approval is now part of the graph and itself explainable
        assertTrue(s.client.explorer().explain(approval.node()) != null);
    }
}
