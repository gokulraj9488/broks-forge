package com.broksforge.modules.organization.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "CreateOrganizationRequest")
public record CreateOrganizationRequest(

        @Schema(example = "Acme AI")
        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 120)
        String name,

        @Schema(description = "Optional URL slug; generated from the name if omitted", example = "acme-ai")
        @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
                message = "Slug may contain only lowercase letters, digits and single hyphens")
        @Size(max = 64)
        String slug,

        @Schema(example = "Building agentic systems for the enterprise")
        @Size(max = 1000)
        String description
) {
}
