package com.broksforge.security.apikey;

import java.util.Optional;

/**
 * Port that resolves a raw API key string into a verified {@link ApiKeyPrincipal}.
 *
 * <p>Implemented by the API key module. Declaring it here lets the security
 * infrastructure authenticate API keys without depending on the module's
 * internals, keeping the modular-monolith boundaries clean.</p>
 */
public interface ApiKeyAuthenticator {

    /**
     * Verifies the supplied raw key and, on success, returns the principal it
     * authenticates. Implementations must run in constant time with respect to
     * the secret and must update last-used metadata as a side effect.
     *
     * @param rawKey the full key as presented by the client (e.g. {@code bf_<id>.<secret>})
     * @return the principal if the key is valid, active and not expired; empty otherwise
     */
    Optional<ApiKeyPrincipal> authenticate(String rawKey);
}
