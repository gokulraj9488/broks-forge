package com.broksforge.modules.model;

import java.util.Map;

/**
 * A concrete HTTP destination for a model invocation — typically a registered
 * agent's endpoint. Carrying the resolved headers (already including any decrypted
 * auth) keeps the {@code model} module decoupled from the agent module's internals:
 * the caller resolves the target, the invoker just calls it.
 *
 * @param endpointUrl the absolute http(s) URL to call
 * @param headers     headers to send (e.g. resolved authentication); never logged
 */
public record ModelTarget(String endpointUrl, Map<String, String> headers) {

    public ModelTarget {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }
}
