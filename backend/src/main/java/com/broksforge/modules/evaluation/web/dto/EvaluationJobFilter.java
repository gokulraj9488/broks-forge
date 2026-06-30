package com.broksforge.modules.evaluation.web.dto;

import com.broksforge.modules.evaluation.domain.EvaluationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "EvaluationJobFilter", description = "Evaluation job search and filter parameters")
public record EvaluationJobFilter(
        @Schema(description = "Free-text match over the job name") String q,
        EvaluationStatus status,
        UUID agentId,
        UUID datasetId
) {
}
