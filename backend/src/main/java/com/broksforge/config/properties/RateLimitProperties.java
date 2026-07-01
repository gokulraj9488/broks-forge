package com.broksforge.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Fixed-window rate-limiting configuration for the sensitive authentication
 * endpoints (registration, login, e-mail verification and password reset), which
 * trigger e-mails and are the natural targets for abuse/brute-force.
 *
 * @param enabled       whether the limiter is active (disable in tests)
 * @param limit         max requests permitted per client IP within the window
 * @param windowSeconds the rolling window length, in seconds
 */
@Validated
@ConfigurationProperties(prefix = "broksforge.security.rate-limit")
public record RateLimitProperties(boolean enabled, int limit, int windowSeconds) {
}
