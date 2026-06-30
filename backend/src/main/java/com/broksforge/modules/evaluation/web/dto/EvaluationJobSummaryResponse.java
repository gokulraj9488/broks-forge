package com.broksforge.modules.evaluation.web.dto;

import com.broksforge.modules.evaluation.domain.EvaluationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "EvaluationJobSummaryResponse", description = "Compact evaluation job listing row")
public record EvaluationJobSummaryResponse(
        UUID id,
        String name,
        EvaluationStatus status,
        UUID agentId,
        UUID datasetId,
        int totalItems,
        int completedItems,
        int failedItems,
        Instant createdAt,
        Instant completedAt
) {
}
