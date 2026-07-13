package com.broksforge.modules.evaluation.service;

import com.broksforge.modules.evaluation.domain.EvaluationMetricType;
import com.broksforge.modules.evaluation.domain.MetricExecutionStatus;

/**
 * Per-metric-type, per-{@link MetricExecutionStatus} count of results that never completed for a
 * job — the signal the root-cause engine uses to report "the judge's provider rejected
 * authentication" instead of "the judge scored responses low" when the judge never actually ran.
 * Excludes {@link MetricExecutionStatus#COMPLETED} by construction (see the repository query).
 */
public record MetricExecutionFailureTally(EvaluationMetricType metricType, MetricExecutionStatus status, Long count) {

    public long countOrZero() {
        return count == null ? 0L : count;
    }
}
