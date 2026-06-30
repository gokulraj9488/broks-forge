package com.broksforge.modules.auth.web.dto;

import com.broksforge.common.validation.StrongPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "ChangePasswordRequest")
public record ChangePasswordRequest(

        @Schema(description = "The user's current password")
        @NotBlank(message = "Current password is required")
        String currentPassword,

        @Schema(description = "The new password")
        @StrongPassword
        String newPassword
) {
}
