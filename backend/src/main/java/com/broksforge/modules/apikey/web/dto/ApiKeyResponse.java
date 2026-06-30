package com.broksforge.modules.apikey.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata about an API key. Never includes the secret.
 */
@Schema(name = "ApiKeyResponse")
public record ApiKeyResponse(
        UUID id,
        UUID organizationId,
        UUID projectId,
        String name,
        @Schema(description = "Public, displayable key prefix", example = "bf_a1b2c3d4e5f6")
        String keyPrefix,
        Instant lastUsedAt,
        Instant expiresAt,
        boolean revoked,
        Instant revokedAt,
        Instant createdAt
) {
}
