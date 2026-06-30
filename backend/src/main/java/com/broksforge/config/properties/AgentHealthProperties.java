package com.broksforge.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent health-check configuration bound from {@code broksforge.agent.health.*}.
 *
 * @param timeoutMs            connect/read timeout for the outbound health probe
 * @param allowPrivateTargets  whether the platform may probe private / loopback
 *                             targets (SSRF guard). Defaults to {@code false} in
 *                             production; enabled in the {@code dev} profile so
 *                             local agents can be probed
 * @param historyWindowDays    rolling window used when computing availability %
 * @param recentLimit          number of recent checks returned in the health summary
 */
@ConfigurationProperties(prefix = "broksforge.agent.health")
public record AgentHealthProperties(
        long timeoutMs,
        boolean allowPrivateTargets,
        int historyWindowDays,
        int recentLimit
) {
}
