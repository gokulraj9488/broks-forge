package com.broksforge.modules.agent.web.dto;

import com.broksforge.modules.agent.domain.AgentAuthType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * Non-sensitive credential metadata. The encrypted secret is never serialized.
 */
@Schema(name = "AgentCredentialResponse")
public record AgentCredentialResponse(
        UUID id,
        UUID agentId,
        @Schema(description = "Human-friendly label")
        String label,
        AgentAuthType authType,
        String username,
        String headerName,
        @Schema(description = "Value prefix, e.g. \"Bearer\"")
        String headerPrefix,
        @Schema(description = "Masked hint of the secret, e.g. ••••ab12")
        String secretHint,
        int keyVersion,
        boolean active,
        @Schema(description = "When the credential was last connection-tested (null if never)")
        Instant lastTestedAt,
        @Schema(description = "Whether the last connection test passed (null if never tested)")
        Boolean lastTestSuccess,
        @Schema(description = "HTTP status from the last connection test")
        Integer lastTestHttpStatus,
        @Schema(description = "Human-readable result of the last connection test")
        String lastTestMessage,
        Instant createdAt,
        Instant updatedAt
) {
}
