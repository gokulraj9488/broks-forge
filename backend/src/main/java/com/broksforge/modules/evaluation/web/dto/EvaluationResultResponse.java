package com.broksforge.modules.evaluation.web.dto;

import com.broksforge.modules.evaluation.domain.EvaluationMetricType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(name = "EvaluationResultResponse", description = "A single metric result for a run")
public record EvaluationResultResponse(
        UUID id,
        EvaluationMetricType metricType,
        String metricLabel,
        boolean passed,
        BigDecimal score,
        BigDecimal threshold,
        String detail
) {
}
