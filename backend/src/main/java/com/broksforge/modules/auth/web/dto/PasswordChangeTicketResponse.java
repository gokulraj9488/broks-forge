package com.broksforge.modules.auth.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Returned when an OTP is verified: a single-use ticket the client presents to
 * the complete step, and when it expires. The ticket is a short-lived
 * continuation token (stored hashed server-side), not a stored credential.
 */
public record PasswordChangeTicketResponse(

        @Schema(description = "Single-use ticket authorising the set-password step")
        String ticket,

        @Schema(description = "When the ticket expires")
        Instant expiresAt

) {
}
