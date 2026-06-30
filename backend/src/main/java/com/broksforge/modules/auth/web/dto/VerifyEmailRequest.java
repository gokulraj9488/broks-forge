package com.broksforge.modules.auth.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "VerifyEmailRequest")
public record VerifyEmailRequest(

        @Schema(description = "The verification token from the verification email")
        @NotBlank(message = "Token is required")
        String token
) {
}
