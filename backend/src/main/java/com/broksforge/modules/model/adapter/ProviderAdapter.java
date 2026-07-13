package com.broksforge.modules.model.adapter;

import com.broksforge.modules.agent.domain.LlmProvider;

import java.util.Map;

/**
 * Provider-specific request construction and response parsing (Provider abstraction
 * milestone). Each hosted LLM provider has its own wire format — different field names for
 * the message list, different token-usage keys, different error envelopes — so this seam
 * isolates that per-provider knowledge instead of it accumulating as special cases inside
 * {@code AgentEndpointInvoker}.
 *
 * <p>Adapters are pure and stateless: no I/O, no decryption. {@code AgentEndpointInvoker}
 * still owns the actual HTTP call and the SSRF guard; {@code AgentCredentialService} still owns
 * decrypting a stored secret. This interface shapes the body sent, interprets the body received,
 * and — since the header shape a secret is presented in is just as provider-specific as the
 * request body (Anthropic's {@code x-api-key}, Google AI Studio's {@code x-goog-api-key}, vs.
 * everyone else's {@code Authorization: Bearer}) — how an already-decrypted secret becomes auth
 * headers, via {@link #buildAuthHeaders}.</p>
 */
public interface ProviderAdapter {

    /** The provider this adapter builds requests/parses responses for. */
    LlmProvider providerType();

    /**
     * True when {@code endpointUrl} is a route this adapter recognises for its provider
     * (e.g. an OpenAI-compatible {@code /chat/completions} path, or Anthropic's
     * {@code /messages}). A host can resolve to a provider type without matching any adapter's
     * route (e.g. a provider's models-list endpoint), in which case the caller falls back to
     * the generic envelope.
     */
    boolean supportsEndpoint(String endpointUrl);

    /**
     * Builds the outbound JSON body. {@code parameters} are the caller's generation
     * parameters (temperature, max_tokens, ...); {@code defaultMaxTokens} is applied only if
     * the caller didn't already specify a completion-length parameter (see
     * {@code ProviderDefaultsProperties}) — never overrides an explicit caller value.
     */
    Map<String, Object> buildPayload(String model, String input, Map<String, Object> parameters,
                                     int defaultMaxTokens);

    /**
     * Parses a successful (2xx) response body into output text + token/cost accounting. A 2xx
     * response is not always a usable result — e.g. Google AI Studio returns HTTP 200 with an
     * empty candidate when a response is blocked by a safety filter — so the result carries an
     * optional {@link ParsedInvocation#blockedReason()} the caller treats as a failure despite
     * the successful HTTP status.
     */
    ParsedInvocation parseSuccess(String body);

    /**
     * Extracts a human-readable message from a non-2xx response body, in this provider's own
     * error envelope shape. Returns {@code null} if the body isn't in a recognised shape (the
     * caller falls back to a raw body snippet).
     */
    String parseError(String body);

    /**
     * Rewrites {@code endpointUrl} for this specific invocation, if this provider embeds the
     * model in the URL path rather than the request body (Google AI Studio's
     * {@code /models/{model}:generateContent}). The default no-op is correct for every
     * message-body-based provider (OpenAI-compatible, Anthropic, Ollama) — only a path-based
     * provider needs to override this.
     */
    default String resolveInvocationUrl(String endpointUrl, String model) {
        return endpointUrl;
    }

    /**
     * Builds the auth header(s) this provider expects for an already-decrypted API key. The
     * default ({@code Authorization: Bearer <key>}) is correct for OpenAI/Groq/OpenRouter;
     * Anthropic and Google AI Studio override it with their own real header convention. Returns
     * an empty map for a blank/null key (nothing to send).
     */
    default Map<String, String> buildAuthHeaders(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return Map.of();
        }
        return Map.of("Authorization", "Bearer " + apiKey);
    }
}
