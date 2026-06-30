package com.broksforge.modules.auth.web.dto;

import com.broksforge.common.validation.StrongPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "ResetPasswordRequest")
public record ResetPasswordRequest(

        @Schema(description = "The reset token from the password reset email")
        @NotBlank(message = "Token is required")
        String token,

        @Schema(description = "The new password")
        @StrongPassword
        String newPassword
) {
}
