package com.broksforge.common.observability;

/**
 * The observed outcome of one {@link ExecutionStage} on the AI Debugger timeline.
 *
 * <p>{@link #NOT_INSTRUMENTED} is a first-class, honest state: the platform does not
 * yet capture spans for that stage (e.g. retrieval or tool calls), so the timeline
 * marks it explicitly rather than implying success.</p>
 */
public enum StageStatus {
    /** Stage completed without issue. */
    OK,
    /** Stage completed but with a concern worth surfacing (e.g. high latency). */
    WARN,
    /** Stage is where the failure occurred. */
    ERROR,
    /** Stage did not run for this execution. */
    SKIPPED,
    /** Stage is not yet instrumented; no span data is available. */
    NOT_INSTRUMENTED
}
