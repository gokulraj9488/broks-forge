package com.broksforge.modules.agent.service;

import com.broksforge.modules.agent.domain.AgentHealthStatus;
import com.broksforge.modules.agent.domain.HealthProbeStrategy;

/**
 * Outcome of a single outbound health probe, independent of persistence.
 *
 * @param status        derived health status
 * @param success       whether the endpoint was reachable and responded acceptably
 * @param httpStatus    HTTP status code observed, if any
 * @param latencyMs     round-trip latency in milliseconds, if measured
 * @param failureReason explanation when the probe did not succeed
 * @param probeStrategy the provider-aware strategy used for this probe
 * @param probeUrl      the effective URL that was probed
 */
public record HealthProbeResult(
        AgentHealthStatus status,
        boolean success,
        Integer httpStatus,
        Long latencyMs,
        String failureReason,
        HealthProbeStrategy probeStrategy,
        String probeUrl
) {
}
