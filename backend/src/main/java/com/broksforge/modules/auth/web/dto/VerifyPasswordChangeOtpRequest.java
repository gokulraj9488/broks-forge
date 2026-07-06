package com.broksforge.modules.auth.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Step 2 of the OTP password change: the 6-digit code the user received by
 * e-mail. A successful verify returns a single-use ticket (see ADR 0017).
 */
public record VerifyPasswordChangeOtpRequest(

        @NotBlank
        @Pattern(regexp = "\\d{6}", message = "must be the 6-digit code from your e-mail")
        @Schema(example = "482913", description = "The 6-digit one-time code sent to your e-mail")
        String code

) {
}
