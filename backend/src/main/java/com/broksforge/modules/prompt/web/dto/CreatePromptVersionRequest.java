package com.broksforge.modules.prompt.web.dto;

import com.broksforge.modules.agent.domain.LlmProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Creates a new immutable prompt version. Variables are derived from the template's
 * {@code {{placeholder}}} tokens server-side, never supplied by the client.
 */
@Schema(name = "CreatePromptVersionRequest", description = "Create a new prompt version")
public record CreatePromptVersionRequest(
        @NotBlank @Size(max = 50_000) String template,
        @Size(max = 1000) String notes,
        @Schema(description = "Optional provider this prompt was authored for") LlmProvider provider,
        @Size(max = 128) String model,
        @Schema(description = "Activate this version immediately") Boolean activate
) {
}
