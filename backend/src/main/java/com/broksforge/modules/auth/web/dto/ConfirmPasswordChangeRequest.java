package com.broksforge.modules.auth.web.dto;

import com.broksforge.common.validation.StrongPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "ConfirmPasswordChangeRequest")
public record ConfirmPasswordChangeRequest(

        @Schema(description = "The confirmation token from the password change email")
        @NotBlank(message = "Token is required")
        String token,

        @Schema(description = "The new password")
        @StrongPassword
        String newPassword
) {
}
