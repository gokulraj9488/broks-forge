package com.broksforge.modules.debugger.web.dto;

import com.broksforge.common.observability.ExecutionStage;
import com.broksforge.common.observability.StageStatus;
import com.broksforge.modules.agent.domain.LlmProvider;
import com.broksforge.modules.evaluation.domain.EvaluationRunStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Response DTOs for the AI Debugger execution timeline (ADR 0014).
 */
public final class DebuggerDtos {

    private DebuggerDtos() {
    }

    @Schema(name = "TimelineStageResponse", description = "One stage of an execution timeline")
    public record TimelineStageResponse(
            ExecutionStage stage,
            String label,
            StageStatus status,
            Long startOffsetMs,
            Long durationMs,
            String detail,
            String explanation
    ) {
    }

    @Schema(name = "ExecutionTimelineResponse",
            description = "A reconstructed, stage-by-stage timeline of a single evaluation run")
    public record ExecutionTimelineResponse(
            UUID jobId,
            UUID runId,
            int sequence,
            EvaluationRunStatus runStatus,
            Boolean passed,
            Long totalLatencyMs,
            LlmProvider provider,
            String model,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens,
            BigDecimal cost,
            List<TimelineStageResponse> stages,
            String failureExplanation,
            boolean tracingActive,
            List<String> notes
    ) {
    }
}
