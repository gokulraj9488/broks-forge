package com.broksforge.modules.evaluation.domain;

/** Lifecycle events recorded by the execution engine for {@link EvaluationJob}'s audit trail. */
public enum EvaluationJobEventType {
    QUEUED,
    STARTED,
    CHECKPOINT,
    RETRY,
    CANCELLED,
    COMPLETED,
    FAILED,
    RESUMED,
    STALL_RECOVERED
}
