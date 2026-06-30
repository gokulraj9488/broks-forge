package com.broksforge.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for outbound model invocations (used by evaluation, benchmarking,
 * and future direct invocation).
 *
 * @param timeoutMs           connect/read timeout for outbound model calls
 * @param allowPrivateTargets SSRF policy: whether private/loopback targets may be reached
 *                            (false in production, true in the dev profile for local agents)
 * @param maxOutputChars      defensive cap on stored output length per invocation
 */
@Validated
@ConfigurationProperties(prefix = "broksforge.model")
public record ModelInvocationProperties(
        long timeoutMs,
        boolean allowPrivateTargets,
        int maxOutputChars
) {
}
