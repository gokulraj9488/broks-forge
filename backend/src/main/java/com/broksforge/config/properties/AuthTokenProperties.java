package com.broksforge.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Lifetimes for single-use auth tokens, bound from
 * {@code broksforge.security.tokens.*}.
 *
 * @param emailVerificationExpirationMs   lifetime of e-mail verification links
 * @param passwordResetExpirationMs       lifetime of password reset links
 * @param passwordChangeExpirationMs      lifetime of password change confirmation links (legacy link flow)
 * @param passwordChangeOtpExpirationMs   lifetime of the emailed password-change OTP (ADR 0017)
 * @param passwordChangeTicketExpirationMs lifetime of the single-use ticket issued once the OTP is verified
 */
@ConfigurationProperties(prefix = "broksforge.security.tokens")
public record AuthTokenProperties(
        long emailVerificationExpirationMs,
        long passwordResetExpirationMs,
        long passwordChangeExpirationMs,
        long passwordChangeOtpExpirationMs,
        long passwordChangeTicketExpirationMs
) {
}
