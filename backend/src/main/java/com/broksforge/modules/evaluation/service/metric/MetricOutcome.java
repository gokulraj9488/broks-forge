package com.broksforge.modules.evaluation.service.metric;

import com.broksforge.modules.evaluation.domain.EvaluationMetricType;

import java.math.BigDecimal;

/**
 * The scored result of a single metric, mapped 1:1 to a persisted
 * {@code EvaluationResult}.
 */
public record MetricOutcome(
        EvaluationMetricType type,
        String label,
        boolean passed,
        BigDecimal score,
        BigDecimal threshold,
        String detail
) {
}
