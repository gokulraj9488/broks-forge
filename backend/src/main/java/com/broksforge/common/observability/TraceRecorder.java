package com.broksforge.common.observability;

/**
 * The recording seam for execution spans. This is the single interface that live
 * agent-execution instrumentation will call to emit a span per {@link ExecutionStage},
 * and the same point where an OpenTelemetry exporter will later attach (ADR 0010,
 * ADR 0014). Keeping it here — dependency-free and provider-agnostic — means business
 * code can be instrumented now without committing to a tracing backend.
 *
 * <p>The platform ships {@link NoOpTraceRecorder} as the default implementation: the
 * architecture is in place, but no exporter is wired yet (a deliberate Phase 4 scope
 * boundary). The AI Debugger reconstructs timelines from persisted evaluation data
 * rather than from recorded spans until this seam is driven by a real exporter.</p>
 */
public interface TraceRecorder {

    /**
     * Records a single completed span for an execution stage. Implementations must be
     * non-blocking and must never throw into the caller's hot path.
     *
     * @param correlationId the logical operation id (see {@code CorrelationIdFilter})
     * @param stage         the execution stage this span represents
     * @param status        the observed outcome of the stage
     * @param durationMs    the wall-clock duration of the stage in milliseconds
     * @param detail        a short, non-sensitive description (no secrets, no PII)
     */
    void recordStage(String correlationId, ExecutionStage stage, StageStatus status, long durationMs, String detail);

    /** Whether spans are actually being exported. {@code false} for the no-op default. */
    boolean isActive();
}
