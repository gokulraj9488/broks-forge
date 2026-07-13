package com.broksforge.modules.evaluation.web.dto;

import com.broksforge.modules.agent.domain.LlmProvider;
import com.broksforge.modules.evaluation.domain.EvaluationStatus;
import com.broksforge.modules.evaluation.domain.EvaluationTargetType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Schema(name = "EvaluationJobResponse", description = "A full evaluation job with its summary")
public record EvaluationJobResponse(
        UUID id,
        UUID organizationId,
        UUID projectId,
        String name,
        UUID ownerId,
        EvaluationStatus status,
        EvaluationTargetType targetType,
        UUID agentId,
        UUID agentVersionId,
        UUID datasetId,
        UUID datasetVersionId,
        UUID promptId,
        UUID promptVersionId,
        UUID profileId,
        UUID profileVersionId,
        Integer profileVersionNumber,
        LlmProvider provider,
        String model,
        Map<String, Object> parameters,
        int totalItems,
        int completedItems,
        int failedItems,
        Instant startedAt,
        Instant completedAt,
        Map<String, Object> summary,
        String errorMessage,
        int priority,
        Instant queuedAt,
        Instant lastProgressAt,
        Integer batchSize,
        int retryCount,
        Instant createdAt,
        Instant updatedAt
) {
}
