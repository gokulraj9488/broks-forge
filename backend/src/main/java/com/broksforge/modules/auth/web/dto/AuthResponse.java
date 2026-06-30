package com.broksforge.modules.auth.web.dto;

import com.broksforge.modules.user.web.dto.UserResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Returned on successful registration, login and token refresh.
 */
@Schema(name = "AuthResponse", description = "Authentication tokens and the authenticated user")
public record AuthResponse(

        @Schema(description = "Short-lived JWT access token")
        String accessToken,

        @Schema(description = "Token type", example = "Bearer")
        String tokenType,

        @Schema(description = "Access token lifetime in seconds", example = "900")
        long expiresIn,

        @Schema(description = "Opaque, revocable refresh token")
        String refreshToken,

        @Schema(description = "The authenticated user")
        UserResponse user
) {
    public static AuthResponse of(String accessToken, long expiresInSeconds, String refreshToken, UserResponse user) {
        return new AuthResponse(accessToken, "Bearer", expiresInSeconds, refreshToken, user);
    }
}
