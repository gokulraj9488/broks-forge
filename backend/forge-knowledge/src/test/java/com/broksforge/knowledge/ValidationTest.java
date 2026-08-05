package com.broksforge.knowledge;

import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.knowledge.graph.KnowledgeGraph;
import com.broksforge.knowledge.graph.KnowledgeObject;
import com.broksforge.knowledge.graph.Link;
import com.broksforge.knowledge.ontology.ObjectTypes;
import com.broksforge.knowledge.ontology.Verbs;
import com.broksforge.knowledge.validate.KnowledgeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The semantic layer rejects ontology-invalid graphs BEFORE they reach the append-only log (KN-0004). */
class ValidationTest {

    private static boolean hasCode(KnowledgeException ex, String code) {
        return ex.result().errors().stream().anyMatch(i -> i.code().equals(code));
    }

    @Test
    @DisplayName("CI-4: an Agent without a Model is rejected (missing required composition)")
    void agentNeedsModel() {
        KnowledgeGraph kg = TestSupport.graph();
        KnowledgeObject prompt = kg.define(ObjectTypes.PROMPT, TestSupport.obj("text", "p"));
        KnowledgeException ex = assertThrows(KnowledgeException.class,
                () -> kg.define(ObjectTypes.AGENT, TestSupport.obj("name", "a"), Link.of(Verbs.USES, prompt)));
        assertTrue(hasCode(ex, "MISSING_RELATION"));
    }

    @Test
    @DisplayName("CI-2: a Claim with no evidence reference is rejected")
    void claimNeedsEvidence() {
        KnowledgeGraph kg = TestSupport.graph();
        KnowledgeException ex = assertThrows(KnowledgeException.class,
                () -> kg.define(ObjectTypes.EVALUATION_VERDICT, CanonicalValue.objectBuilder()
                        .put("statement", "s").put("method", "m:v1")
                        .put("confidence", CanonicalValue.of(new BigDecimal("0.5"))).build()));
        assertTrue(hasCode(ex, "CLAIM_EVIDENCE"));
    }

    @Test
    @DisplayName("endpoint type: an illegal composition target is rejected")
    void endpointTypeChecked() {
        KnowledgeGraph kg = TestSupport.graph();
        KnowledgeObject provider = kg.define(ObjectTypes.PROVIDER, TestSupport.obj("name", "p"));
        KnowledgeObject prompt = kg.define(ObjectTypes.PROMPT, TestSupport.obj("text", "x"));
        // A Model may use a Provider but not a Prompt.
        KnowledgeException ex = assertThrows(KnowledgeException.class,
                () -> kg.define(ObjectTypes.MODEL, TestSupport.obj("model_id", "m"),
                        Link.of(Verbs.USES, provider), Link.of(Verbs.USES, prompt)));
        assertTrue(hasCode(ex, "ENDPOINT_TYPE"));
    }

    @Test
    @DisplayName("payload schema: a missing required field is rejected")
    void missingField() {
        KnowledgeGraph kg = TestSupport.graph();
        KnowledgeException ex = assertThrows(KnowledgeException.class,
                () -> kg.define(ObjectTypes.TOOL, CanonicalValue.objectBuilder()
                        .put("name", "search").put("input_schema", CanonicalValue.objectBuilder().build())
                        .build())); // missing side_effect
        assertTrue(hasCode(ex, "MISSING_FIELD"));
    }

    @Test
    @DisplayName("payload schema: a wrong field type is rejected")
    void wrongFieldType() {
        KnowledgeGraph kg = TestSupport.graph();
        KnowledgeException ex = assertThrows(KnowledgeException.class,
                () -> kg.define(ObjectTypes.ENVIRONMENT, CanonicalValue.objectBuilder()
                        .put("name", "prod").put("tier", CanonicalValue.of(1)).build())); // tier must be STRING
        assertTrue(hasCode(ex, "FIELD_TYPE"));
    }

    @Test
    @DisplayName("CI-6: an Observation (Run) is never revised (the KAP-3 discipline, in userspace)")
    void observationsAreImmutable() {
        KnowledgeGraph kg = TestSupport.graph();
        KnowledgeObject provider = kg.define(ObjectTypes.PROVIDER, TestSupport.obj("name", "p"));
        KnowledgeObject model = kg.define(ObjectTypes.MODEL, TestSupport.obj("model_id", "m"), Link.of(Verbs.USES, provider));
        KnowledgeObject prompt = kg.define(ObjectTypes.PROMPT, TestSupport.obj("text", "p"));
        KnowledgeObject agent = kg.define(ObjectTypes.AGENT, TestSupport.obj("name", "a"),
                Link.of(Verbs.USES, model), Link.of(Verbs.USES, prompt));
        KnowledgeObject run = kg.define(ObjectTypes.RUN, TestSupport.obj("status", "ok"), Link.of(Verbs.EXECUTED, agent));
        KnowledgeException ex = assertThrows(KnowledgeException.class,
                () -> kg.addRevision(run, TestSupport.obj("status", "edited")));
        assertTrue(hasCode(ex, "IMMUTABLE"));
    }

    @Test
    @DisplayName("bounded union: a Run may execute an Agent or Workflow, but not a Prompt (ARB refinement)")
    void executedIsBoundedUnion() {
        KnowledgeGraph kg = TestSupport.graph();
        KnowledgeObject prompt = kg.define(ObjectTypes.PROMPT, TestSupport.obj("text", "p"));
        KnowledgeException ex = assertThrows(KnowledgeException.class,
                () -> kg.define(ObjectTypes.RUN, TestSupport.obj("status", "ok"), Link.of(Verbs.EXECUTED, prompt)));
        assertTrue(hasCode(ex, "ENDPOINT_TYPE"));
    }

    @Test
    @DisplayName("relate: an intrinsic verb cannot be asserted as an extrinsic edge; a causal edge can")
    void extrinsicRules() {
        KnowledgeGraph kg = TestSupport.graph();
        KnowledgeGraphTest.Scenario s = KnowledgeGraphTest.build(kg);
        KnowledgeObject incident = kg.define(ObjectTypes.INCIDENT, TestSupport.obj("severity", "high"));
        // caused (causality, extrinsic) Deployment -> Incident is legal.
        assertDoesNotThrow(() -> kg.relate(s.deployment, Verbs.CAUSED, incident));
        // uses (composition, intrinsic) cannot be asserted after creation.
        KnowledgeException ex = assertThrows(KnowledgeException.class,
                () -> kg.relate(s.agent, Verbs.USES, s.model));
        assertTrue(hasCode(ex, "INTRINSIC_AS_EXTRINSIC"));
    }
}
