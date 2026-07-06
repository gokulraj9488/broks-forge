package com.broksforge.modules.auth.web.dto;

import com.broksforge.common.validation.StrongPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Step 3 of the OTP password change: the single-use ticket returned by the
 * verify step plus the new password. Applying it revokes every session.
 */
public record CompletePasswordChangeRequest(

        @NotBlank
        @Schema(description = "The single-use ticket returned by the verify step")
        String ticket,

        @StrongPassword
        @Schema(example = "N3wStr0ngPass", description = "The new password (min 8 chars, upper, lower and a digit)")
        String newPassword

) {
}
