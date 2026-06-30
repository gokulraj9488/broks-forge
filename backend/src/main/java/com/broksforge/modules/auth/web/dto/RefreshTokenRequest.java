package com.broksforge.modules.auth.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "RefreshTokenRequest")
public record RefreshTokenRequest(

        @Schema(description = "A valid refresh token previously issued by the API")
        @NotBlank(message = "Refresh token is required")
        String refreshToken
) {
}
