package com.broksforge.modules.agent.web.dto;

import com.broksforge.modules.agent.domain.DeploymentEnvironment;
import com.broksforge.modules.agent.domain.LlmProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request to register a new deployment (version) of an agent.
 */
@Schema(name = "RegisterAgentVersionRequest")
public record RegisterAgentVersionRequest(

        @Schema(example = "1.4.0")
        @NotBlank(message = "Version number is required")
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._+-]{0,63}$",
                message = "Version number may contain letters, digits and . _ + -")
        String versionNumber,

        @Schema(example = "claude-opus-4-8")
        @NotBlank(message = "Model is required")
        @Size(max = 128)
        String model,

        @NotNull(message = "Provider is required")
        LlmProvider provider,

        @Schema(example = "1.0.0")
        @Size(max = 64)
        String frameworkVersion,

        @Schema(example = "a1b2c3d4e5f6")
        @Pattern(regexp = "^[0-9a-fA-F]{7,64}$", message = "Git commit SHA must be 7-64 hex characters")
        String gitCommitSha,

        @Schema(description = "Reference to a prompt version (future Prompt Management module)")
        @Size(max = 64)
        String promptVersion,

        @NotNull(message = "Environment is required")
        DeploymentEnvironment environment,

        @Size(max = 2000)
        String releaseNotes,

        @Schema(description = "Whether this version can be rolled back to later", defaultValue = "true")
        Boolean rollbackReady,

        @Schema(description = "Activate this version immediately on registration", defaultValue = "false")
        Boolean activate
) {
}
