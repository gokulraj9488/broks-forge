package com.broksforge.modules.prompt.web.dto;

import com.broksforge.modules.prompt.domain.PromptStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(name = "PromptResponse", description = "A prompt-library entry")
public record PromptResponse(
        UUID id,
        UUID organizationId,
        UUID projectId,
        String name,
        String slug,
        String description,
        UUID ownerId,
        PromptStatus status,
        List<String> tags,
        int latestVersionNumber,
        UUID currentActiveVersionId,
        Instant createdAt,
        Instant updatedAt
) {
}
