package com.broksforge.knowledge.ontology;

import com.broksforge.kernel.api.Kind;
import com.broksforge.kernel.api.Verb;
import com.broksforge.knowledge.spi.OntologyModule;

import static com.broksforge.knowledge.ontology.ObjectTypes.*;

/**
 * Factory for the canonical Forge Knowledge Ontology — every catalog object type and relationship type,
 * registered as data. Expressed as an {@link OntologyModule} so third parties can compose additional
 * modules on top ({@code Ontology.builder(); Ontologies.forge().contribute(b); myModule.contribute(b);}).
 */
public final class Ontologies {

    private Ontologies() {
    }

    /** @return the canonical, consistent Forge ontology */
    public static Ontology forge() {
        Ontology.Builder b = Ontology.builder();
        canonicalModule().contribute(b);
        return b.build();
    }

    /** @return the canonical ontology as a composable module */
    public static OntologyModule canonicalModule() {
        return Ontologies::contribute;
    }

    private static void contribute(Ontology.Builder b) {
        // ---- object types (the full catalog) --------------------------------------------------
        for (ObjectType t : new ObjectType[]{
                PROMPT, PROVIDER, MODEL, TOOL, AGENT, WORKFLOW, DATASET, KNOWLEDGE_BASE, MEMORY_STORE,
                EVALUATION, EXPERIMENT, BENCHMARK, ENVIRONMENT, POLICY, GUARDRAIL, ARTIFACT_PACKAGE,
                RUN, SESSION, INCIDENT, HUMAN_FEEDBACK, MEMORY_ENTRY,
                EVALUATION_VERDICT, EXPERIMENT_CONCLUSION, BENCHMARK_SCORE, CAPABILITY, ROOT_CAUSE, COST_ROLLUP,
                DEPLOYMENT, PROMOTION, ROLLBACK, APPROVAL, RETIREMENT}) {
            b.object(t);
        }

        // ---- composition ----------------------------------------------------------------------
        rel(b, Verbs.USES, AGENT.name(), MODEL.name(), Cardinality.EXACTLY_ONE);          // CI-4
        rel(b, Verbs.USES, AGENT.name(), PROMPT.name(), Cardinality.ONE_OR_MORE);          // CI-4
        rel(b, Verbs.USES, AGENT.name(), TOOL.name(), Cardinality.ANY);
        rel(b, Verbs.USES, AGENT.name(), KNOWLEDGE_BASE.name(), Cardinality.ANY);
        rel(b, Verbs.USES, AGENT.name(), GUARDRAIL.name(), Cardinality.ANY);
        rel(b, Verbs.USES, MODEL.name(), PROVIDER.name(), Cardinality.EXACTLY_ONE);        // CI-7
        rel(b, Verbs.USES, EVALUATION.name(), DATASET.name(), Cardinality.ONE_OR_MORE);
        rel(b, Verbs.USES, EVALUATION.name(), MODEL.name(), Cardinality.ANY);              // judge
        rel(b, Verbs.USES, BENCHMARK.name(), DATASET.name(), Cardinality.ONE_OR_MORE);
        rel(b, Verbs.USES, EXPERIMENT.name(), EVALUATION.name(), Cardinality.ANY);
        rel(b, Verbs.USES, GUARDRAIL.name(), TOOL.name(), Cardinality.ANY);               // checker
        rel(b, Verbs.CONTAINS, WORKFLOW.name(), AGENT.name(), Cardinality.ONE_OR_MORE);    // CI-5
        rel(b, Verbs.CONTAINS, WORKFLOW.name(), TOOL.name(), Cardinality.ANY);
        rel(b, Verbs.CONTAINS, WORKFLOW.name(), WORKFLOW.name(), Cardinality.ANY);
        rel(b, Verbs.CONTAINS, SESSION.name(), RUN.name(), Cardinality.ONE_OR_MORE);
        rel(b, Verbs.CONTAINS, EXPERIMENT.name(), AGENT.name(), Cardinality.AT_LEAST_TWO); // variants
        rel(b, Verbs.INDEXES, KNOWLEDGE_BASE.name(), DATASET.name(), Cardinality.ONE_OR_MORE); // CI-5
        rel(b, Verbs.ENFORCES, GUARDRAIL.name(), POLICY.name(), Cardinality.ONE_OR_MORE);  // CI-5
        relToKind(b, Verbs.INCLUDES, ARTIFACT_PACKAGE.name(), Kind.ARTIFACT, Cardinality.ONE_OR_MORE, true); // CI-5
        relKindToKind(b, Verbs.DEPENDS_ON, Kind.ARTIFACT, Kind.ARTIFACT, Cardinality.ANY, true);

        // ---- derivation -----------------------------------------------------------------------
        relKindToKind(b, Verbs.DERIVED_FROM, Kind.ARTIFACT, Kind.ARTIFACT, Cardinality.ZERO_OR_ONE, true);
        rel(b, Verbs.FINE_TUNED_FROM, MODEL.name(), MODEL.name(), Cardinality.ZERO_OR_ONE);
        relKindToKind(b, Verbs.SUPERSEDES, Kind.CLAIM, Kind.CLAIM, Cardinality.ZERO_OR_ONE, true);
        b.relation(RelationType.verb(Verbs.EXECUTED).from(RUN.name())
                .toAny(AGENT.name(), WORKFLOW.name()).card(Cardinality.EXACTLY_ONE).build());       // CI-1
        rel(b, Verbs.GENERATED_BY, MEMORY_ENTRY.name(), RUN.name(), Cardinality.EXACTLY_ONE);

        // ---- evidence (from Claims) -----------------------------------------------------------
        relKindToKind(b, Verbs.CITES, Kind.CLAIM, Kind.OBSERVATION, Cardinality.ANY, true);
        relKindToKind(b, Verbs.CITES, Kind.CLAIM, Kind.CLAIM, Cardinality.ANY, true);
        relFromKindToKind(b, Verbs.MEASURED_BY, Kind.CLAIM, Kind.ARTIFACT, Cardinality.ZERO_OR_ONE, true);
        relKindToKind(b, Verbs.SUPPORTS, Kind.CLAIM, Kind.CLAIM, Cardinality.ANY, false);
        relKindToKind(b, Verbs.SUPPORTS, Kind.OBSERVATION, Kind.CLAIM, Cardinality.ANY, false);
        relKindToKind(b, Verbs.REFUTES, Kind.CLAIM, Kind.CLAIM, Cardinality.ANY, false);
        relKindToKind(b, Verbs.REFUTES, Kind.OBSERVATION, Kind.CLAIM, Cardinality.ANY, false);

        // ---- causality (extrinsic) ------------------------------------------------------------
        relExtrinsic(b, Verbs.CAUSED, DEPLOYMENT.name(), INCIDENT.name(), Cardinality.ANY);
        relExtrinsic(b, Verbs.TRIGGERED, RUN.name(), INCIDENT.name(), Cardinality.ANY);
        // Runtime causality between runs — multi-agent handoffs, tool-triggered sub-runs, autonomous
        // chains (Knowledge Governance refinement KG-R1).
        relExtrinsic(b, Verbs.TRIGGERED, RUN.name(), RUN.name(), Cardinality.ANY);
        relFromTypeToKind(b, Verbs.DETECTED_BY, ROOT_CAUSE.name(), Kind.OBSERVATION, Cardinality.ANY, false);
        relFromKindToKind(b, Verbs.REGRESSED, Kind.OBSERVATION, Kind.ARTIFACT, Cardinality.ANY, false);

        // ---- derivation: process provenance (extrinsic) ---------------------------------------
        // An artifact was produced by the run that emitted it (fine-tuned model, generated prompt/tool,
        // compiled package) — the artifact↔process link Phase 3 version control needs (KG-R2).
        b.relation(RelationType.verb(Verbs.PRODUCED_BY).fromKind(Kind.ARTIFACT).to(RUN.name())
                .card(Cardinality.ZERO_OR_ONE).extrinsic().build());

        // ---- intent (the decision surface) ----------------------------------------------------
        b.relation(RelationType.verb(Verbs.APPLIED).from(DEPLOYMENT.name())
                .toAny(AGENT.name(), WORKFLOW.name(), ARTIFACT_PACKAGE.name())
                .card(Cardinality.EXACTLY_ONE).build());                                             // CI-3
        rel(b, Verbs.TARGETS, DEPLOYMENT.name(), ENVIRONMENT.name(), Cardinality.EXACTLY_ONE);          // CI-3
        relFromKindToKind(b, Verbs.RESTS_ON, Kind.DECISION, Kind.CLAIM, Cardinality.ANY, true);
        relFromKindToKind(b, Verbs.PROPOSES, Kind.DECISION, Kind.ARTIFACT, Cardinality.ZERO_OR_ONE, true);
        relFromKindToKind(b, Verbs.APPROVES, Kind.DECISION, Kind.DECISION, Cardinality.ZERO_OR_ONE, false);
        relFromKindToKind(b, Verbs.REJECTS, Kind.DECISION, Kind.DECISION, Cardinality.ZERO_OR_ONE, false);
    }

    // ---- registration helpers ----------------------------------------------------------------

    private static void rel(Ontology.Builder b, Verb v, String from, String to, Cardinality c) {
        b.relation(RelationType.verb(v).from(from).to(to).card(c).build());
    }

    private static void relExtrinsic(Ontology.Builder b, Verb v, String from, String to, Cardinality c) {
        b.relation(RelationType.verb(v).from(from).to(to).card(c).extrinsic().build());
    }

    private static void relToKind(Ontology.Builder b, Verb v, String from, Kind toKind, Cardinality c, boolean intrinsic) {
        RelationType.Builder rb = RelationType.verb(v).from(from).toKind(toKind).card(c);
        if (!intrinsic) {
            rb.extrinsic();
        }
        b.relation(rb.build());
    }

    private static void relFromTypeToKind(Ontology.Builder b, Verb v, String from, Kind toKind, Cardinality c, boolean intrinsic) {
        relToKind(b, v, from, toKind, c, intrinsic);
    }

    private static void relKindToKind(Ontology.Builder b, Verb v, Kind fromKind, Kind toKind, Cardinality c, boolean intrinsic) {
        RelationType.Builder rb = RelationType.verb(v).fromKind(fromKind).toKind(toKind).card(c);
        if (!intrinsic) {
            rb.extrinsic();
        }
        b.relation(rb.build());
    }

    private static void relFromKindToKind(Ontology.Builder b, Verb v, Kind fromKind, Kind toKind, Cardinality c, boolean intrinsic) {
        relKindToKind(b, v, fromKind, toKind, c, intrinsic);
    }
}
