package com.broksforge.knowledge;

import com.broksforge.kernel.api.Kind;
import com.broksforge.knowledge.ontology.ObjectType;
import com.broksforge.knowledge.ontology.Ontologies;
import com.broksforge.knowledge.ontology.Ontology;
import com.broksforge.knowledge.ontology.RelationType;
import com.broksforge.knowledge.ontology.Verbs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The ontology must be internally consistent (the Phase 2 success criterion). */
class OntologyConsistencyTest {

    private final Ontology ontology = Ontologies.forge();

    @Test
    @DisplayName("the canonical ontology builds (no duplicate names or (kind,subtype))")
    void buildsConsistently() {
        assertFalse(ontology.objectTypes().isEmpty());
        assertFalse(ontology.relationTypes().isEmpty());
    }

    @Test
    @DisplayName("object type names and (kind,subtype) pairs are unique")
    void noDuplicates() {
        Set<String> names = new HashSet<>();
        Set<String> subs = new HashSet<>();
        for (ObjectType t : ontology.objectTypes()) {
            assertTrue(names.add(t.name()), "duplicate name " + t.name());
            assertTrue(subs.add(t.kind() + "/" + t.subtype()), "duplicate subtype " + t.subtype());
        }
    }

    @Test
    @DisplayName("all four kernel kinds are represented (epistemic completeness)")
    void allKindsPresent() {
        for (Kind kind : Kind.values()) {
            assertTrue(ontology.objectTypes().stream().anyMatch(t -> t.kind() == kind),
                    "no object type of kind " + kind);
        }
    }

    @Test
    @DisplayName("every relation endpoint named by type resolves, and every verb has one catalog family")
    void relationsWellFormed() {
        for (RelationType r : ontology.relationTypes()) {
            if (r.fromType() != null) {
                assertTrue(ontology.type(r.fromType()).isPresent(), "unknown from " + r.fromType());
            }
            if (r.toType() != null) {
                assertTrue(ontology.type(r.toType()).isPresent(), "unknown to " + r.toType());
            }
            Verbs.byName(r.verb().name()).ifPresent(canonical ->
                    assertEquals(canonical.family(), r.verb().family(),
                            "verb " + r.verb().name() + " family disagreement"));
        }
    }

    @Test
    @DisplayName("the definition/result split holds: evaluation/experiment/benchmark each have Artifact + Claim")
    void definitionResultSplit() {
        for (String[] pair : new String[][]{
                {"Evaluation", "EvaluationVerdict"},
                {"Experiment", "ExperimentConclusion"},
                {"Benchmark", "BenchmarkScore"}}) {
            assertEquals(Kind.ARTIFACT, ontology.type(pair[0]).orElseThrow().kind());
            assertEquals(Kind.CLAIM, ontology.type(pair[1]).orElseThrow().kind());
        }
    }

    @Test
    @DisplayName("Cost is not a first-class object; CostRollup is a Claim")
    void costIsNotAnObject() {
        assertTrue(ontology.type("Cost").isEmpty());
        assertEquals(Kind.CLAIM, ontology.type("CostRollup").orElseThrow().kind());
    }

    @Test
    @DisplayName("Deployment is a Decision (not an Artifact)")
    void deploymentIsDecision() {
        assertEquals(Kind.DECISION, ontology.type("Deployment").orElseThrow().kind());
    }
}
