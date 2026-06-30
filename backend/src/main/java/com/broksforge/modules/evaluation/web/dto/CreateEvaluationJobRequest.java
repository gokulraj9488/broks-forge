package com.broksforge.modules.evaluation.web.dto;

import com.broksforge.modules.agent.domain.LlmProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

/**
 * Creates an evaluation job. The dataset/prompt versions are pinned at creation: when
 * omitted, the dataset's current version and the prompt's active version are used and
 * recorded for reproducibility. Set {@code autoRun} to execute immediately.
 */
@Schema(name = "CreateEvaluationJobRequest", description = "Create (and optionally run) an evaluation job")
public record CreateEvaluationJobRequest(
        @NotBlank @Size(max = 160) String name,
        @NotNull UUID agentId,
        UUID agentVersionId,
        @NotNull UUID datasetId,
        UUID datasetVersionId,
        UUID promptId,
        UUID promptVersionId,
        UUID profileId,
        LlmProvider provider,
        @Size(max = 128) String model,
        Map<String, Object> parameters,
        @Schema(description = "Execute the job immediately after creation") Boolean autoRun
) {
}
