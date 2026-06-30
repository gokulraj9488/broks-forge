package com.broksforge.modules.evaluation.domain;

/**
 * Lifecycle of an {@link EvaluationJob} — the top-level evaluation aggregate. A job
 * starts {@code PENDING}, transitions to {@code RUNNING} when execution begins, and
 * settles in a terminal state. This is the seam an async worker will drive when
 * execution moves off the request thread (see ADR 0005).
 */
public enum EvaluationStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}
