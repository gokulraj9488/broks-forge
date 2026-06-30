package com.broksforge.modules.agent.domain;

/**
 * The latest known health of an agent endpoint.
 */
public enum AgentHealthStatus {

    /** No health check has been performed yet. */
    UNKNOWN,

    /** Endpoint responded successfully. */
    HEALTHY,

    /** Endpoint responded, but with a client error or warning condition. */
    DEGRADED,

    /** Endpoint failed to respond or returned a server error. */
    UNHEALTHY
}
