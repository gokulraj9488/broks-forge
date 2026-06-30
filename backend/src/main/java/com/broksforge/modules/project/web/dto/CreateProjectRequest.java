package com.broksforge.modules.project.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "CreateProjectRequest")
public record CreateProjectRequest(

        @Schema(example = "Customer Support Agent")
        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 120)
        String name,

        @Schema(description = "Optional slug; generated from the name if omitted", example = "customer-support-agent")
        @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
                message = "Slug may contain only lowercase letters, digits and single hyphens")
        @Size(max = 64)
        String slug,

        @Schema(example = "Production support agent for tier-1 tickets")
        @Size(max = 1000)
        String description
) {
}
