package com.broksforge.modules.evaluation.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reconstructs exactly how a run's prompt was rendered, computed on read from the run, the job's
 * pinned prompt version and the dataset item it used — no new table, following the same pattern
 * as the AI Debugger's execution timeline. Exists to make a silently-empty template variable
 * (see {@code EvaluationJobExecutor#logRenderDiagnostics}) visible in the UI, not just server logs.
 */
@Schema(name = "PromptRenderDebugResponse", description = "How a run's prompt template resolved against its dataset row")
public record PromptRenderDebugResponse(
        UUID runId,
        UUID datasetItemId,
        int datasetItemSequence,
        @Schema(description = "Every {{variable}} the prompt template references") List<String> variablesDetected,
        @Schema(description = "The variables that had a non-null value at render time, and what it was")
        Map<String, String> variablesResolved,
        @Schema(description = "The exact prompt text sent to the model for this run") String renderedPrompt,
        @Schema(description = "Variables the template references that had no matching value — these rendered "
                + "as empty text, which is almost always the cause of a model reporting missing data")
        List<String> missingVariables
) {
}
