package com.broksforge.modules.prompt.web.dto;

import com.broksforge.modules.prompt.domain.PromptStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(name = "PromptSummaryResponse", description = "Compact prompt listing row")
public record PromptSummaryResponse(
        UUID id,
        String name,
        String slug,
        PromptStatus status,
        List<String> tags,
        int latestVersionNumber,
        UUID currentActiveVersionId,
        Instant updatedAt
) {
}
