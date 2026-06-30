package com.broksforge.modules.prompt.web.dto;

import com.broksforge.modules.prompt.domain.PromptStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.Set;

@Schema(name = "UpdatePromptRequest", description = "Update prompt metadata")
public record UpdatePromptRequest(
        @Size(max = 120) String name,
        @Size(max = 1000) String description,
        PromptStatus status,
        @Size(max = 32) Set<@Size(max = 40) String> tags
) {
}
