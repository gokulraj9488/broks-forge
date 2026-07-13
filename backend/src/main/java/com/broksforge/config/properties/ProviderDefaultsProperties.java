package com.broksforge.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Locale;
import java.util.Map;

/**
 * Per-provider defaults for outbound model invocations, keyed by a short provider name
 * (e.g. {@code openai}, {@code groq}, {@code openrouter}, {@code google}, {@code anthropic},
 * {@code mistral}, {@code deepseek}, {@code cohere}). Configured under
 * {@code broksforge.providers.default-max-tokens.<provider>}, with {@code fallback-max-tokens}
 * used for a provider not explicitly listed.
 *
 * <p>Exists so a completion budget is never left to whatever a provider does when
 * {@code max_tokens} is omitted (some providers fall back to the full model context window,
 * which can silently exhaust an account's credit balance on a single request — see
 * {@code AgentEndpointInvoker}). Values here only apply when the caller (job/profile
 * generation parameters) didn't already specify {@code max_tokens} or
 * {@code max_completion_tokens}; a caller-supplied value always wins.</p>
 *
 * @param defaultMaxTokens  provider name (lowercase) → default completion token cap
 * @param fallbackMaxTokens used when the target provider isn't in {@code defaultMaxTokens}
 *                          (unrecognised host, or a provider not yet listed here)
 */
@Validated
@ConfigurationProperties(prefix = "broksforge.providers")
public record ProviderDefaultsProperties(Map<String, Integer> defaultMaxTokens, int fallbackMaxTokens) {

    /** Resolves the configured default for {@code providerKey} (case-insensitive), or the fallback. */
    public int maxTokensFor(String providerKey) {
        if (providerKey == null || defaultMaxTokens == null) {
            return fallbackMaxTokens;
        }
        Integer configured = defaultMaxTokens.get(providerKey.toLowerCase(Locale.ROOT));
        return configured != null ? configured : fallbackMaxTokens;
    }
}
