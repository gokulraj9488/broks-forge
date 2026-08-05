package com.broksforge.knowledge.ontology;

import com.broksforge.kernel.api.Kind;

/**
 * Constants for the well-known knowledge object types — the AI-Engineering catalog as code (KN-0001
 * exposes the data as constants for compile-time ergonomics). Every entry here is registered into the
 * canonical ontology by {@link Ontologies#forge()}.
 */
public final class ObjectTypes {

    private ObjectTypes() {
    }

    // ---- Artifacts (intent) ------------------------------------------------------------------

    public static final ObjectType PROMPT = ObjectType.of("Prompt", Kind.ARTIFACT, "prompt",
            PayloadSchema.builder().required("text", FieldType.STRING)
                    .optional("variables", FieldType.ARRAY).roles("system", "user", "tool").build());

    public static final ObjectType PROVIDER = ObjectType.of("Provider", Kind.ARTIFACT, "provider",
            PayloadSchema.builder().required("name", FieldType.STRING)
                    .optional("endpoints", FieldType.ARRAY).optional("auth_handle", FieldType.STRING).build());

    public static final ObjectType MODEL = ObjectType.of("Model", Kind.ARTIFACT, "model",
            PayloadSchema.builder().required("model_id", FieldType.STRING)
                    .optional("context_window", FieldType.NUMBER).build());

    public static final ObjectType TOOL = ObjectType.of("Tool", Kind.ARTIFACT, "tool",
            PayloadSchema.builder().required("name", FieldType.STRING)
                    .required("input_schema", FieldType.OBJECT)
                    .required("side_effect", FieldType.STRING).build());

    public static final ObjectType AGENT = ObjectType.of("Agent", Kind.ARTIFACT, "agent",
            PayloadSchema.builder().optional("name", FieldType.STRING)
                    .optional("description", FieldType.STRING).build());

    public static final ObjectType WORKFLOW = ObjectType.of("Workflow", Kind.ARTIFACT, "workflow",
            PayloadSchema.builder().optional("name", FieldType.STRING).build());

    public static final ObjectType DATASET = ObjectType.of("Dataset", Kind.ARTIFACT, "dataset",
            PayloadSchema.builder().required("content_hash", FieldType.STRING)
                    .optional("size", FieldType.NUMBER).optional("schema", FieldType.OBJECT)
                    .roles("evaluation-set", "training-set", "retrieval-corpus").build());

    public static final ObjectType KNOWLEDGE_BASE = ObjectType.of("KnowledgeBase", Kind.ARTIFACT, "knowledge-base",
            PayloadSchema.builder().required("index_type", FieldType.STRING)
                    .optional("embedding_model_ref", FieldType.STRING).build());

    public static final ObjectType MEMORY_STORE = ObjectType.of("MemoryStore", Kind.ARTIFACT, "memory-store",
            PayloadSchema.builder().required("scope", FieldType.STRING)
                    .optional("strategy", FieldType.STRING).optional("retention", FieldType.STRING).build());

    public static final ObjectType EVALUATION = ObjectType.of("Evaluation", Kind.ARTIFACT, "evaluation",
            PayloadSchema.builder().required("metrics", FieldType.ARRAY)
                    .optional("criteria", FieldType.OBJECT).optional("subject_type", FieldType.STRING)
                    .roles("offline", "online").build());

    public static final ObjectType EXPERIMENT = ObjectType.of("Experiment", Kind.ARTIFACT, "experiment",
            PayloadSchema.builder().required("hypothesis", FieldType.STRING)
                    .required("metric", FieldType.STRING).build());

    public static final ObjectType BENCHMARK = ObjectType.of("Benchmark", Kind.ARTIFACT, "benchmark",
            PayloadSchema.builder().required("metric", FieldType.STRING).optional("scope", FieldType.STRING).build());

    public static final ObjectType ENVIRONMENT = ObjectType.of("Environment", Kind.ARTIFACT, "environment",
            PayloadSchema.builder().required("name", FieldType.STRING).required("tier", FieldType.STRING).build());

    public static final ObjectType POLICY = ObjectType.of("Policy", Kind.ARTIFACT, "policy",
            PayloadSchema.builder().required("rule", FieldType.OBJECT)
                    .optional("scope", FieldType.STRING).optional("severity", FieldType.STRING).build());

    public static final ObjectType GUARDRAIL = ObjectType.of("Guardrail", Kind.ARTIFACT, "guardrail",
            PayloadSchema.builder().required("stage", FieldType.STRING).required("action", FieldType.STRING).build());

    public static final ObjectType ARTIFACT_PACKAGE = ObjectType.of("ArtifactPackage", Kind.ARTIFACT, "artifact-package",
            PayloadSchema.builder().optional("name", FieldType.STRING).optional("version", FieldType.STRING).build());

    // ---- Observations (reality) --------------------------------------------------------------

    public static final ObjectType RUN = ObjectType.of("Run", Kind.OBSERVATION, "run",
            PayloadSchema.builder().required("status", FieldType.STRING)
                    .optional("inputs", FieldType.OBJECT).optional("outputs", FieldType.OBJECT)
                    .optional("latency_ms", FieldType.NUMBER).optional("cost", FieldType.OBJECT)
                    .optional("closure_hash", FieldType.STRING).build());

    public static final ObjectType SESSION = ObjectType.of("Session", Kind.OBSERVATION, "session",
            PayloadSchema.builder().optional("started", FieldType.STRING).optional("channel", FieldType.STRING).build());

    public static final ObjectType INCIDENT = ObjectType.of("Incident", Kind.OBSERVATION, "incident",
            PayloadSchema.builder().required("severity", FieldType.STRING)
                    .optional("detected_at", FieldType.STRING).optional("symptoms", FieldType.ARRAY).build());

    public static final ObjectType HUMAN_FEEDBACK = ObjectType.of("HumanFeedback", Kind.OBSERVATION, "human-feedback",
            PayloadSchema.builder().required("signal", FieldType.ANY).optional("rater", FieldType.STRING).build());

    public static final ObjectType MEMORY_ENTRY = ObjectType.of("MemoryEntry", Kind.OBSERVATION, "memory-entry",
            PayloadSchema.builder().optional("content", FieldType.ANY).build());

    // ---- Claims (belief) — inherit the kernel Claim law (statement + method + confidence) -----

    private static PayloadSchema.Builder claim() {
        return PayloadSchema.builder()
                .required("statement", FieldType.STRING)
                .required("method", FieldType.STRING)
                .required("confidence", FieldType.NUMBER);
    }

    public static final ObjectType EVALUATION_VERDICT = ObjectType.of("EvaluationVerdict", Kind.CLAIM, "evaluation-verdict", claim().build());
    public static final ObjectType EXPERIMENT_CONCLUSION = ObjectType.of("ExperimentConclusion", Kind.CLAIM, "experiment-conclusion", claim().build());
    public static final ObjectType BENCHMARK_SCORE = ObjectType.of("BenchmarkScore", Kind.CLAIM, "benchmark-score", claim().build());
    public static final ObjectType CAPABILITY = ObjectType.of("Capability", Kind.CLAIM, "capability", claim().build());
    public static final ObjectType ROOT_CAUSE = ObjectType.of("RootCause", Kind.CLAIM, "root-cause", claim().build());
    public static final ObjectType COST_ROLLUP = ObjectType.of("CostRollup", Kind.CLAIM, "cost-rollup", claim().build());

    // ---- Decisions (will) — inherit the kernel Decision law -----------------------------------

    private static PayloadSchema.Builder decision() {
        return PayloadSchema.builder()
                .optional("statement", FieldType.STRING)
                .optional("judgment-call", FieldType.BOOL);
    }

    public static final ObjectType DEPLOYMENT = ObjectType.of("Deployment", Kind.DECISION, "deployment", decision().build());
    public static final ObjectType PROMOTION = ObjectType.of("Promotion", Kind.DECISION, "promotion", decision().build());
    public static final ObjectType ROLLBACK = ObjectType.of("Rollback", Kind.DECISION, "rollback", decision().build());
    public static final ObjectType APPROVAL = ObjectType.of("Approval", Kind.DECISION, "approval", decision().build());
    public static final ObjectType RETIREMENT = ObjectType.of("Retirement", Kind.DECISION, "retirement", decision().build());
}
