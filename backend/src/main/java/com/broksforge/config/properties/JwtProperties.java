package com.broksforge.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JWT configuration bound from {@code broksforge.security.jwt.*}.
 *
 * @param secret                  Base64-encoded HMAC-SHA signing secret (>= 256 bits)
 * @param issuer                  value placed in the {@code iss} claim
 * @param accessTokenExpirationMs lifetime of access tokens, in milliseconds
 * @param refreshTokenExpirationMs lifetime of refresh tokens, in milliseconds
 */
@Validated
@ConfigurationProperties(prefix = "broksforge.security.jwt")
public record JwtProperties(

        @NotBlank(message = "broksforge.security.jwt.secret must be provided")
        String secret,

        @NotBlank
        String issuer,

        @Positive
        long accessTokenExpirationMs,

        @Positive
        long refreshTokenExpirationMs
) {
}
