package com.broksforge.modules.agent.web.dto;

import com.broksforge.modules.agent.domain.AgentAuthType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request to set (replace) an agent's credential. The secret is write-only — it
 * is encrypted on receipt and never returned. Field requirements per auth type
 * are validated in the service layer:
 * <ul>
 *     <li>NONE — no fields</li>
 *     <li>API_KEY — secret (+ optional headerName, defaults to a standard header)</li>
 *     <li>BEARER_TOKEN — secret</li>
 *     <li>BASIC_AUTH — username + secret (password)</li>
 *     <li>CUSTOM_HEADER — headerName + secret</li>
 * </ul>
 */
@Schema(name = "SetAgentCredentialRequest")
public record SetAgentCredentialRequest(

        @NotNull(message = "Authentication type is required")
        AgentAuthType authType,

        @Schema(description = "The secret value (API key / token / password / header value). Write-only.")
        @Size(max = 4096)
        String secret,

        @Schema(description = "Username for BASIC_AUTH")
        @Size(max = 256)
        String username,

        @Schema(description = "Header name for API_KEY / CUSTOM_HEADER")
        @Size(max = 128)
        String headerName
) {
}
