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
        AgentAuthType authType,
        String username,
        String headerName,
        @Schema(description = "Masked hint of the secret, e.g. ••••ab12")
        String secretHint,
        int keyVersion,
        boolean active,
        Instant createdAt
) {
}
