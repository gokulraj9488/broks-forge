package com.broksforge.modules.prompt.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * The result of comparing two prompt versions: the two snapshots plus the variable
 * delta and whether the template text is identical.
 */
@Schema(name = "PromptComparisonResponse", description = "Diff between two prompt versions")
public record PromptComparisonResponse(
        UUID promptId,
        PromptVersionResponse from,
        PromptVersionResponse to,
        List<String> addedVariables,
        List<String> removedVariables,
        List<String> commonVariables,
        boolean identicalTemplate
) {
}
