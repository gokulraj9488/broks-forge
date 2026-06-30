package com.broksforge.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Lifetimes for single-use auth tokens, bound from
 * {@code broksforge.security.tokens.*}.
 *
 * @param emailVerificationExpirationMs lifetime of e-mail verification links
 * @param passwordResetExpirationMs     lifetime of password reset links
 */
@ConfigurationProperties(prefix = "broksforge.security.tokens")
public record AuthTokenProperties(
        long emailVerificationExpirationMs,
        long passwordResetExpirationMs
) {
}
