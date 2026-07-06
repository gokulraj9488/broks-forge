package com.broksforge.modules.agent.domain;

import com.broksforge.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A single health-check observation for an agent endpoint. Records accumulate to
 * form a history from which availability is computed. The architecture is
 * scheduler-ready: an automated scheduler (future) records rows with
 * {@code checkType = SCHEDULED} via the same path used by manual checks.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "agent_health_checks",
        indexes = {
                @Index(name = "idx_agent_health_checks_agent", columnList = "agent_id"),
                @Index(name = "idx_agent_health_checks_agent_time", columnList = "agent_id, checked_at")
        }
)
public class AgentHealthCheck extends BaseEntity {

    @Column(name = "agent_id", nullable = false)
    private UUID agentId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    /** The agent version that was active when the check ran (may be null). */
    @Column(name = "version_id")
    private UUID versionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "check_type", nullable = false, length = 32)
    private HealthCheckType checkType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AgentHealthStatus status;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    /** The provider-aware strategy used for this probe (may be null for legacy rows). */
    @Enumerated(EnumType.STRING)
    @Column(name = "probe_strategy", length = 32)
    private HealthProbeStrategy probeStrategy;

    /** The effective URL that was probed (may differ from the endpoint, e.g. {base}/actuator/health). */
    @Column(name = "probe_url", length = 2048)
    private String probeUrl;
}
