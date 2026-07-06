package com.broksforge.modules.agent.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Outcome of a credential connection test against the agent endpoint. Carries no
 * secret — only the reachability/auth verdict and timing.
 */
@Schema(name = "CredentialTestResponse")
public record CredentialTestResponse(
        @Schema(description = "Whether the endpoint accepted the credential (2xx/3xx)")
        boolean success,
        @Schema(description = "HTTP status returned by the endpoint, if any")
        Integer httpStatus,
        @Schema(description = "Round-trip latency in milliseconds")
        long latencyMs,
        @Schema(description = "Human-readable result, e.g. \"Connected successfully (HTTP 200)\"")
        String message,
        @Schema(description = "When the test ran")
        Instant testedAt
) {
}
