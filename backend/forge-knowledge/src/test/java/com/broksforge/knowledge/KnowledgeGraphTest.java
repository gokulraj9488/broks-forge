package com.broksforge.knowledge;

import com.broksforge.kernel.api.Address;
import com.broksforge.kernel.api.EdgeFamily;
import com.broksforge.kernel.api.Name;
import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.knowledge.graph.KnowledgeGraph;
import com.broksforge.knowledge.graph.KnowledgeObject;
import com.broksforge.knowledge.graph.KnowledgeView;
import com.broksforge.knowledge.graph.Link;
import com.broksforge.knowledge.ontology.ObjectTypes;
import com.broksforge.knowledge.ontology.Verbs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end: build the typed engineering loop through the ontology, then read it back and audit. */
class KnowledgeGraphTest {

    /** A reusable valid scenario spanning all four kinds and the definition/result split. */
    static final class Scenario {
        KnowledgeObject provider, model, prompt, agent, dataset, evaluation, run, verdict, environment, deployment;
    }

    static Scenario build(KnowledgeGraph kg) {
        Scenario s = new Scenario();
        s.provider = kg.define(ObjectTypes.PROVIDER, TestSupport.obj("name", "anthropic"));
        s.model = kg.define(ObjectTypes.MODEL, TestSupport.obj("model_id", "claude-sonnet-5"),
                Link.of(Verbs.USES, s.provider));
        s.prompt = kg.define(ObjectTypes.PROMPT, TestSupport.obj("text", "Answer: {{ticket}}"));
        s.agent = kg.define(ObjectTypes.AGENT, TestSupport.obj("name", "support-agent"),
                Link.of(Verbs.USES, s.model), Link.of(Verbs.USES, s.prompt));
        s.dataset = kg.define(ObjectTypes.DATASET,
                CanonicalValue.objectBuilder().put("content_hash", "sha-256:abc").put("role", "evaluation-set").build());
        s.evaluation = kg.define(ObjectTypes.EVALUATION,
                CanonicalValue.objectBuilder()
                        .put("metrics", CanonicalValue.array(CanonicalValue.of("helpfulness"))).build(),
                Link.of(Verbs.USES, s.dataset));
        s.run = kg.define(ObjectTypes.RUN, TestSupport.obj("status", "ok"),
                Link.of(Verbs.EXECUTED, s.agent));
        s.verdict = kg.define(ObjectTypes.EVALUATION_VERDICT,
                CanonicalValue.objectBuilder()
                        .put("statement", "agent is helpful")
                        .put("method", "llm-judge:v1")
                        .put("confidence", CanonicalValue.of(new BigDecimal("0.82")))
                        .build(),
                Link.of(Verbs.CITES, s.run), Link.of(Verbs.MEASURED_BY, s.evaluation));
        s.environment = kg.define(ObjectTypes.ENVIRONMENT,
                CanonicalValue.objectBuilder().put("name", "prod").put("tier", "prod").build());
        s.deployment = kg.deploy(Name.of("deploy/prod/support-agent"), ObjectTypes.DEPLOYMENT,
                s.agent, s.environment, List.of(s.verdict), "ship the helpful agent");
        return s;
    }

    @Test
    @DisplayName("the full typed engineering loop builds and validates end-to-end")
    void buildsTheLoop() {
        KnowledgeGraph kg = TestSupport.graph();
        Scenario s = build(kg);
        assertEquals(com.broksforge.kernel.api.Kind.ARTIFACT, s.agent.type().kind());
        assertEquals(com.broksforge.kernel.api.Kind.OBSERVATION, s.run.type().kind());
        assertEquals(com.broksforge.kernel.api.Kind.CLAIM, s.verdict.type().kind());
        assertEquals(com.broksforge.kernel.api.Kind.DECISION, s.deployment.type().kind());
        assertTrue(kg.kernel().verifyChain(kg.org()));
    }

    @Test
    @DisplayName("deployment repoints the name; resolve returns the deployed agent revision")
    void deploymentResolves() {
        KnowledgeGraph kg = TestSupport.graph();
        Scenario s = build(kg);
        Optional<Address.Revision> resolved = kg.resolve(Name.of("deploy/prod/support-agent"));
        assertEquals(s.agent.hash(), resolved.orElseThrow().revision());
    }

    @Test
    @DisplayName("the projection types every node and surfaces the five families")
    void projectionTypesAndRelations() {
        KnowledgeGraph kg = TestSupport.graph();
        build(kg);
        KnowledgeView view = kg.view();
        assertEquals(1, view.count(ObjectTypes.AGENT));
        assertEquals(1, view.count(ObjectTypes.RUN));
        assertEquals(1, view.count(ObjectTypes.EVALUATION_VERDICT));
        assertEquals(1, view.count(ObjectTypes.DEPLOYMENT));
        assertTrue(view.untypedSubtypes().isEmpty(), "every node should be typed by the ontology");

        // families present: composition (uses), derivation (executed), evidence (cites), intent (applied/targets/rests_on)
        var families = new java.util.HashSet<EdgeFamily>();
        view.relationships().forEach(r -> families.add(r.family()));
        assertTrue(families.contains(EdgeFamily.COMPOSITION));
        assertTrue(families.contains(EdgeFamily.DERIVATION));
        assertTrue(families.contains(EdgeFamily.EVIDENCE));
        assertTrue(families.contains(EdgeFamily.INTENT));
    }

    @Test
    @DisplayName("KG-R1/KG-R2: multi-agent handoff (Run→Run) and process provenance (Artifact→Run) model cleanly")
    void governanceRefinements() {
        KnowledgeGraph kg = TestSupport.graph();
        Scenario s = build(kg);
        // A second agent whose run is triggered by the first run (multi-agent handoff).
        KnowledgeObject agentB = kg.define(ObjectTypes.AGENT, TestSupport.obj("name", "triage-agent"),
                Link.of(Verbs.USES, s.model), Link.of(Verbs.USES, s.prompt));
        KnowledgeObject runB = kg.define(ObjectTypes.RUN, TestSupport.obj("status", "ok"),
                Link.of(Verbs.EXECUTED, agentB));
        // runB was triggered by the first run (causality, Run→Run).
        kg.relate(runB, Verbs.TRIGGERED, s.run);
        // A fine-tuned model produced by a run (process provenance, Artifact→Run).
        KnowledgeObject tuned = kg.define(ObjectTypes.MODEL, TestSupport.obj("model_id", "sonnet-ft"),
                Link.of(Verbs.USES, s.provider), Link.of(Verbs.FINE_TUNED_FROM, s.model));
        kg.relate(tuned, Verbs.PRODUCED_BY, s.run);

        var families = new java.util.HashSet<EdgeFamily>();
        kg.view().relationships().forEach(r -> families.add(r.family()));
        assertTrue(families.contains(EdgeFamily.CAUSALITY), "Run→Run triggered should appear");
        assertTrue(kg.kernel().verifyChain(kg.org()));
    }

    @Test
    @DisplayName("versioning an artifact works; the agent's composition closure is its certificate")
    void versioningAndClosure() {
        KnowledgeGraph kg = TestSupport.graph();
        Scenario s = build(kg);
        KnowledgeObject promptV2 = kg.addRevision(s.prompt, TestSupport.obj("text", "Answer concisely: {{ticket}}"),
                Link.of(Verbs.DERIVED_FROM, s.prompt));
        assertEquals(s.prompt.node(), promptV2.node());
        // closure of the agent includes its model and prompt (kernel op, reused).
        var closure = kg.kernel().closure(s.agent.hash());
        assertTrue(closure.containsKey(s.agent.hash()));
        assertTrue(closure.containsKey(s.model.hash()));
        assertTrue(closure.containsKey(s.prompt.hash()));
    }
}
