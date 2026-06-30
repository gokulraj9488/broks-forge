package com.broksforge.modules.agent.web.dto;

import com.broksforge.common.validation.ValidEndpointUrl;
import com.broksforge.modules.agent.domain.AgentAuthType;
import com.broksforge.modules.agent.domain.AgentFramework;
import com.broksforge.modules.agent.domain.AgentLanguage;
import com.broksforge.modules.agent.domain.AgentVisibility;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Request to register a new agent. Server-controlled fields (owner, organization,
 * project, status, health, active version) are intentionally absent to prevent
 * mass assignment.
 */
@Schema(name = "RegisterAgentRequest")
public record RegisterAgentRequest(

        @Schema(example = "Customer Support Agent")
        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 120)
        String name,

        @Schema(description = "Optional slug; generated from the name if omitted", example = "customer-support-agent")
        @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
                message = "Slug may contain only lowercase letters, digits and single hyphens")
        @Size(max = 64)
        String slug,

        @Schema(example = "Tier-1 support agent backed by Spring AI")
        @Size(max = 1000)
        String description,

        @NotNull(message = "Visibility is required")
        AgentVisibility visibility,

        @NotNull(message = "Framework is required")
        AgentFramework framework,

        @NotNull(message = "Language is required")
        AgentLanguage language,

        @Schema(example = "https://agents.example.com/support")
        @NotBlank(message = "Endpoint URL is required")
        @ValidEndpointUrl
        String endpointUrl,

        @NotNull(message = "Authentication type is required")
        AgentAuthType authType,

        @Valid
        AgentCapabilitiesDto capabilities,

        @Schema(description = "Optional labels for organising and filtering agents")
        @Size(max = 25, message = "An agent may have at most 25 tags")
        Set<@Size(max = 64) String> tags
) {
}
