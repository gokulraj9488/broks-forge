package com.broksforge.modules.evaluation.web.dto;

import com.broksforge.modules.evaluation.domain.EvaluationRunStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "EvaluationRunResponse", description = "A single evaluation run (one dataset item)")
public record EvaluationRunResponse(
        UUID id,
        int sequence,
        @Schema(description = "1 for the first try; N means the row was retried N-1 times "
                + "(background retry on 429/5xx, or a later resumed pass)") int attempt,
        EvaluationRunStatus status,
        UUID datasetItemId,
        String input,
        String output,
        Long latencyMs,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        BigDecimal cost,
        Integer httpStatus,
        Boolean passed,
        BigDecimal score,
        String error,
        Instant completedAt
) {
}
