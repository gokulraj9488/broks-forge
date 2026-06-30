package com.broksforge.common.observability;

/**
 * The canonical stages of a single agent execution, in causal order. This is the
 * shared vocabulary for the AI Debugger's execution timeline and for the future
 * distributed-tracing instrumentation: each stage maps onto one span when
 * OpenTelemetry is adopted (see ADR 0010, ADR 0014).
 *
 * <p>Today only the stages the platform can observe from persisted evaluation data
 * ({@code PROMPT}, {@code MODEL}, {@code PARSER}, {@code OUTPUT}) are populated;
 * {@code MEMORY}, {@code RETRIEVER} and {@code TOOLS} are declared here so the
 * timeline shape is stable and forward-compatible, and are reported as
 * {@link StageStatus#NOT_INSTRUMENTED} until live tracing lands (Phase 5/6).</p>
 */
public enum ExecutionStage {

    /** Rendering the prompt template with the run's variables. */
    PROMPT("Prompt", "Prompt template rendered with input variables"),
    /** Reading/writing agent memory or conversation state. */
    MEMORY("Memory", "Agent memory / conversation state access"),
    /** Retrieval-augmented generation: query, retrieved chunks, grounding. */
    RETRIEVER("Retriever", "Context retrieval (RAG)"),
    /** Tool / function calls the agent performs. */
    TOOLS("Tools", "Tool and function invocations"),
    /** The model (or agent endpoint) invocation itself. */
    MODEL("Model", "Model / agent endpoint invocation"),
    /** Parsing and validating the raw model output. */
    PARSER("Parser", "Output parsing and structural validation"),
    /** The final output handed back and scored. */
    OUTPUT("Output", "Final output produced and scored");

    private final String label;
    private final String description;

    ExecutionStage(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }
}
