package com.broksforge.modules.evaluation.service;

import com.broksforge.modules.evaluation.domain.EvaluationMetricType;

/**
 * Published per-metric pass/fail tally for a job, used by the root-cause engine to
 * identify the dominant failure mode. Fields are boxed because they originate from a
 * JPQL {@code SUM(...)} constructor expression (consistent with the other aggregate
 * projections in this module).
 */
public record MetricFailureTally(EvaluationMetricType metricType, Long passed, Long failed) {

    public long passedOrZero() {
        return passed == null ? 0L : passed;
    }

    public long failedOrZero() {
        return failed == null ? 0L : failed;
    }
}
