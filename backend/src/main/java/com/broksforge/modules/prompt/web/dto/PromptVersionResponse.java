package com.broksforge.modules.prompt.web.dto;

import com.broksforge.modules.agent.domain.LlmProvider;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(name = "PromptVersionResponse", description = "An immutable prompt version")
public record PromptVersionResponse(
        UUID id,
        UUID promptId,
        int versionNumber,
        String template,
        List<String> variables,
        String notes,
        LlmProvider provider,
        String model,
        boolean active,
        Instant createdAt
) {
}
