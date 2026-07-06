package com.broksforge.modules.agent.web.dto;

import com.broksforge.modules.agent.domain.AgentAuthType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request to dry-run a connection test for a not-yet-saved credential, so a user
 * can verify a secret works before storing it. The secret is used only for the
 * single outbound probe and is never persisted.
 */
@Schema(name = "TestAgentCredentialRequest")
public record TestAgentCredentialRequest(

        @NotNull(message = "Authentication type is required")
        AgentAuthType authType,

        @Schema(description = "The secret to test. Write-only; not stored.", example = "gsk_xxxxxxxxxxxxxxxx")
        @Size(max = 4096)
        String secret,

        @Schema(description = "Username for BASIC_AUTH")
        @Size(max = 256)
        String username,

        @Schema(description = "Header name for API_KEY / CUSTOM_HEADER", example = "Authorization")
        @Size(max = 128)
        String headerName,

        @Schema(description = "Optional value prefix, e.g. \"Bearer\" or \"Token\"", example = "Bearer")
        @Size(max = 64)
        String headerPrefix
) {
}
