package com.broksforge.modules.prompt.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

@Schema(name = "CreatePromptRequest", description = "Create a prompt-library entry")
public record CreatePromptRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 64) String slug,
        @Size(max = 1000) String description,
        @Size(max = 32) Set<@Size(max = 40) String> tags
) {
}
