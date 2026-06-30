package com.broksforge.modules.agent.domain;

/**
 * How the platform authenticates when calling an agent's endpoint.
 */
public enum AgentAuthType {

    /** No authentication. */
    NONE,

    /** A secret presented as an API key header. */
    API_KEY,

    /** A bearer token presented in the Authorization header. */
    BEARER_TOKEN,

    /** HTTP Basic authentication (username + password). */
    BASIC_AUTH,

    /** A secret presented in a caller-defined custom header. */
    CUSTOM_HEADER;

    /** @return whether this auth type requires a stored secret value. */
    public boolean requiresSecret() {
        return this != NONE;
    }
}
