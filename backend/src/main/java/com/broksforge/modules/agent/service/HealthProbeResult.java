package com.broksforge.modules.agent.service;

import com.broksforge.modules.agent.domain.AgentHealthStatus;

/**
 * Outcome of a single outbound health probe, independent of persistence.
 *
 * @param status        derived health status
 * @param success       whether the endpoint was reachable and responded acceptably
 * @param httpStatus    HTTP status code observed, if any
 * @param latencyMs     round-trip latency in milliseconds, if measured
 * @param failureReason explanation when the probe did not succeed
 */
public record HealthProbeResult(
        AgentHealthStatus status,
        boolean success,
        Integer httpStatus,
        Long latencyMs,
        String failureReason
) {
}
