package com.broksforge.modules.evaluation.service.metric;

import com.broksforge.modules.evaluation.domain.EvaluationMetricType;
import com.broksforge.modules.evaluation.domain.MetricExecutionStatus;

import java.math.BigDecimal;

/**
 * The scored result of a single metric, mapped 1:1 to a persisted {@code EvaluationResult}.
 * {@code passed}/{@code score} are {@code null} unless {@code executionStatus} is
 * {@link MetricExecutionStatus#COMPLETED} — a metric that never ran (provider auth failure,
 * timeout, ...) has no score to report, only the reason it didn't run.
 */
public record MetricOutcome(
        EvaluationMetricType type,
        String label,
        Boolean passed,
        BigDecimal score,
        BigDecimal threshold,
        String detail,
        MetricExecutionStatus executionStatus
) {
    /** Convenience constructor for the common case: a metric that actually completed. */
    public MetricOutcome(EvaluationMetricType type, String label, boolean passed, BigDecimal score,
                         BigDecimal threshold, String detail) {
        this(type, label, (Boolean) passed, score, threshold, detail, MetricExecutionStatus.COMPLETED);
    }
}
