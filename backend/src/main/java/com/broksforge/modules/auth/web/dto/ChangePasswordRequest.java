package com.broksforge.modules.auth.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Starts the verified password-change flow: the current password is checked and
 * a one-time confirmation link is e-mailed. The new password is supplied later,
 * on {@code POST /api/v1/auth/confirm-password-change}.
 */
@Schema(name = "ChangePasswordRequest")
public record ChangePasswordRequest(

        @Schema(description = "The user's current password")
        @NotBlank(message = "Current password is required")
        String currentPassword
) {
}
