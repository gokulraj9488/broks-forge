package com.broksforge.fxp.workflow;

import com.broksforge.fkge.explain.Explanation;
import com.broksforge.fkge.index.GraphNode;
import com.broksforge.fkge.reason.CausalTrace;
import com.broksforge.fxp.ForgeClient;
import com.broksforge.fxp.FxpTestSupportBridge;
import com.broksforge.fxp.copilot.ForgeCopilot;
import com.broksforge.fxp.copilot.GroundedAnswer;
import com.broksforge.fxp.copilot.Intent;
import com.broksforge.fxp.copilot.TemplateLanguageModel;
import com.broksforge.fxp.explore.ProductionDossier;
import com.broksforge.fvcs.repo.Branch;
import com.broksforge.fvcs.repo.CommitRef;
import com.broksforge.fvcs.repo.SnapshotRef;
import com.broksforge.fvcs.repo.TagRole;
import com.broksforge.kernel.api.Kind;
import com.broksforge.knowledge.graph.KnowledgeObject;
import com.broksforge.knowledge.ontology.ObjectTypes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The three mission reference workflows, end to end, through the FXP experiences over the real platform. */
class ReferenceWorkflowsTest {

    @Test
    @DisplayName("W1: change → version → evaluate → claim → decide → promote → explain")
    void w1_changeToExplanation() {
        ForgeClient client = FxpTestSupportBridge.freshClient();
        var studio = client.studio();

        // change a prompt and cut a version
        KnowledgeObject provider = studio.create(ObjectTypes.PROVIDER, FxpTestSupportBridge.obj("name", "anthropic"));
        KnowledgeObject model = studio.create(ObjectTypes.MODEL, FxpTestSupportBridge.obj("model_id", "sonnet-5"),
                com.broksforge.knowledge.graph.Link.of(com.broksforge.knowledge.ontology.Verbs.USES, provider));
        KnowledgeObject prompt = studio.create(ObjectTypes.PROMPT, FxpTestSupportBridge.obj("text", "v2 tone"));
        KnowledgeObject agent = studio.create(ObjectTypes.AGENT,
                com.broksforge.kernel.api.canonical.CanonicalValue.objectBuilder().put("name", "bot").build(),
                com.broksforge.knowledge.graph.Link.of(com.broksforge.knowledge.ontology.Verbs.USES, model),
                com.broksforge.knowledge.graph.Link.of(com.broksforge.knowledge.ontology.Verbs.USES, prompt));
        Branch main = studio.branch("main");
        SnapshotRef snap = studio.snapshot("v2", List.of(provider, model, prompt, agent));
        CommitRef commit = studio.commit(main, snap, "improve tone");

        // evaluate → observe → claim → decide
        KnowledgeObject run = studio.recordObservation(ObjectTypes.RUN, FxpTestSupportBridge.obj("status", "success"),
                com.broksforge.knowledge.graph.Link.of(com.broksforge.knowledge.ontology.Verbs.EXECUTED, agent));
        KnowledgeObject verdict = studio.authorClaim(ObjectTypes.EVALUATION_VERDICT,
                "meets bar", "offline-eval", new BigDecimal("0.92"),
                com.broksforge.knowledge.graph.Link.of(com.broksforge.knowledge.ontology.Verbs.CITES, run));
        KnowledgeObject env = studio.create(ObjectTypes.ENVIRONMENT,
                com.broksforge.kernel.api.canonical.CanonicalValue.objectBuilder().put("name", "prod").put("tier", "production").build());
        KnowledgeObject deployment = studio.recordDecision(ObjectTypes.DEPLOYMENT, "ship v2", false,
                com.broksforge.knowledge.graph.Link.of(com.broksforge.knowledge.ontology.Verbs.APPLIED, agent),
                com.broksforge.knowledge.graph.Link.of(com.broksforge.knowledge.ontology.Verbs.TARGETS, env),
                com.broksforge.knowledge.graph.Link.of(com.broksforge.knowledge.ontology.Verbs.RESTS_ON, verdict));

        // promote (tag) and explain
        studio.tag("release/1.4", commit, TagRole.RELEASE, "GA");
        Explanation why = client.explorer().explain(deployment.node());
        assertTrue(why.complete(), () -> "the deployment must be fully explained; gaps=" + why.gaps());
        assertTrue(why.steps().stream().anyMatch(st -> st.to().equals(run.node())),
                "the proof reaches the run observation that grounds the evaluation");
    }

    @Test
    @DisplayName("W2: incident → root cause → provenance → responsible evaluation → reproducible explanation")
    void w2_incidentToRootCause() {
        var s = FxpTestSupportBridge.scenario();
        // W2 step 1: the scenario already recorded an incident caused by the deployment.
        CausalTrace trace = s.client().explorer().rootCause(s.incident().node());
        assertTrue(trace.causes().stream().anyMatch(g -> g.id().equals(s.deployment().node())), "cause is the deployment");
        assertTrue(trace.sound(), "the cause precedes the incident");

        // walk provenance of the cause and find the responsible evaluation
        var prov = s.client().explorer().provenance(s.deployment().node());
        boolean reachesVerdict = prov.contains(s.verdict().node());
        assertTrue(reachesVerdict, "provenance reaches the evaluation the decision rested on");

        // reproducible, grounded explanation
        ForgeCopilot copilot = s.client().copilot(new TemplateLanguageModel());
        GroundedAnswer answer = copilot.ask(s.incident().node(), Intent.ROOT_CAUSE);
        assertTrue(answer.grounded());
        assertFalse(answer.proof().empty());
    }

    @Test
    @DisplayName("W3: 'why is this in production?' — a deterministic, evidence-backed dossier")
    void w3_whyInProduction() {
        var s = FxpTestSupportBridge.scenario();
        ProductionDossier dossier = s.client().explorer().dossier(s.deployment().node());

        assertFalse(dossier.provenance().ancestors().isEmpty(), "history/provenance present");
        assertTrue(dossier.decisionProof().complete(), "decision proof complete");
        assertTrue(dossier.confidence().defined(), "confidence present");
        // every ancestor is a real platform node (evidence-backed, not narrated)
        assertTrue(dossier.provenance().ancestors().stream().map(GraphNode::kind).anyMatch(k -> k == Kind.CLAIM),
                "the dossier includes the evaluation claims behind the decision");

        // The executive-facing narration is grounded and reproducible.
        GroundedAnswer answer = s.client().copilot(new TemplateLanguageModel())
                .ask(s.deployment().node(), Intent.WHY_IN_PRODUCTION);
        assertTrue(answer.grounded());
    }
}
