package com.broksforge.modules.evaluation.domain;

/**
 * Outcome of a single {@link EvaluationRun} (one dataset item executed against the
 * target). {@code SUCCEEDED} means the invocation completed and metrics were scored;
 * {@code FAILED} means the invocation errored (the job continues with other items).
 */
public enum EvaluationRunStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    SKIPPED
}
