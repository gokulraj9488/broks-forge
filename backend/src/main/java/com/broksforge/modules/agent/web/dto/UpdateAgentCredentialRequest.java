package com.broksforge.modules.agent.web.dto;

import com.broksforge.modules.agent.domain.AgentAuthType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request to update an existing credential in place (as opposed to replacing it
 * with a new one). The secret is write-only and <em>optional</em>: leave it
 * blank to keep the stored secret and only change metadata (label, header name,
 * header prefix); provide it to rotate the secret. Per-type field requirements
 * are validated in the service layer.
 */
@Schema(name = "UpdateAgentCredentialRequest")
public record UpdateAgentCredentialRequest(

        @Schema(description = "Human-friendly label", example = "Groq production key")
        @Size(max = 120)
        String label,

        @NotNull(message = "Authentication type is required")
        AgentAuthType authType,

        @Schema(description = "New secret value. Leave blank to keep the current secret. Write-only.")
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
