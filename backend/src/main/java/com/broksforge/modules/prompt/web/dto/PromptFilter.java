package com.broksforge.modules.prompt.web.dto;

import com.broksforge.modules.prompt.domain.PromptStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PromptFilter", description = "Prompt search and filter parameters")
public record PromptFilter(
        @Schema(description = "Free-text match over name, slug and description") String q,
        PromptStatus status,
        @Schema(description = "Exact tag match") String tag
) {
}
