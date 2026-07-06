package com.broksforge.modules.agent.service;

import com.broksforge.common.web.PageResponse;
import com.broksforge.config.properties.AgentHealthProperties;
import com.broksforge.modules.agent.domain.Agent;
import com.broksforge.modules.agent.domain.AgentHealthCheck;
import com.broksforge.modules.agent.domain.AgentVersion;
import com.broksforge.modules.agent.domain.HealthCheckType;
import com.broksforge.modules.agent.repository.AgentHealthCheckRepository;
import com.broksforge.modules.agent.repository.AgentVersionRepository;
import com.broksforge.modules.agent.web.AgentHealthCheckMapper;
import com.broksforge.modules.agent.web.dto.AgentHealthCheckResponse;
import com.broksforge.modules.agent.web.dto.AgentHealthSummaryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Records and reports agent health.
 *
 * <p>{@link #runManualCheck} executes a probe and persists the observation; the
 * same recording path is what a future scheduler will use for automated checks.
 * Availability is computed on read over a rolling window, so no denormalised
 * counters need maintaining.</p>
 */
@Slf4j
@Service
public class AgentHealthService {

    private final AgentHealthCheckRepository healthCheckRepository;
    private final AgentVersionRepository agentVersionRepository;
    private final AgentAccessGuard accessGuard;
    private final AgentHealthCheckExecutor executor;
    private final AgentHealthCheckMapper mapper;
    private final AgentHealthProperties properties;

    public AgentHealthService(AgentHealthCheckRepository healthCheckRepository,
                              AgentVersionRepository agentVersionRepository,
                              AgentAccessGuard accessGuard,
                              AgentHealthCheckExecutor executor,
                              AgentHealthCheckMapper mapper,
                              AgentHealthProperties properties) {
        this.healthCheckRepository = healthCheckRepository;
        this.agentVersionRepository = agentVersionRepository;
        this.accessGuard = accessGuard;
        this.executor = executor;
        this.mapper = mapper;
        this.properties = properties;
    }

    @Transactional
    public AgentHealthCheckResponse runManualCheck(UUID actorId, UUID organizationId, UUID projectId, UUID agentId) {
        Agent agent = accessGuard.requireReadable(organizationId, projectId, agentId, actorId);
        accessGuard.ensureNotArchived(agent);

        // Load the active version so the probe can be provider-aware (provider/model live on the version).
        AgentVersion activeVersion = agent.getCurrentActiveVersionId() != null
                ? agentVersionRepository.findByIdAndAgentId(agent.getCurrentActiveVersionId(), agentId).orElse(null)
                : null;

        HealthProbeResult result = executor.probe(agent, activeVersion);
        Instant checkedAt = Instant.now();

        AgentHealthCheck check = new AgentHealthCheck();
        check.setAgentId(agentId);
        check.setOrganizationId(organizationId);
        check.setProjectId(projectId);
        check.setVersionId(activeVersion != null ? activeVersion.getId() : agent.getCurrentActiveVersionId());
        check.setCheckType(HealthCheckType.MANUAL);
        check.setStatus(result.status());
        check.setSuccess(result.success());
        check.setHttpStatus(result.httpStatus());
        check.setLatencyMs(result.latencyMs());
        check.setCheckedAt(checkedAt);
        check.setFailureReason(result.failureReason());
        check.setProbeStrategy(result.probeStrategy());
        check.setProbeUrl(result.probeUrl());
        AgentHealthCheck saved = healthCheckRepository.save(check);

        agent.applyHealth(result.status(), checkedAt);

        log.info("Manual health check for agent {} by {}: {} ({}ms)",
                agentId, actorId, result.status(), result.latencyMs());
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public AgentHealthSummaryResponse getSummary(UUID actorId, UUID organizationId, UUID projectId, UUID agentId) {
        Agent agent = accessGuard.requireReadable(organizationId, projectId, agentId, actorId);

        Instant windowStart = Instant.now().minus(properties.historyWindowDays(), ChronoUnit.DAYS);
        long total = healthCheckRepository.countByAgentIdAndCheckedAtAfter(agentId, windowStart);
        long successful = healthCheckRepository.countByAgentIdAndSuccessTrueAndCheckedAtAfter(agentId, windowStart);
        Double availability = total == 0 ? null
                : BigDecimal.valueOf(successful * 100.0 / total).setScale(2, RoundingMode.HALF_UP).doubleValue();

        List<AgentHealthCheckResponse> recent = healthCheckRepository
                .findByAgentIdOrderByCheckedAtDesc(agentId, PageRequest.of(0, properties.recentLimit()))
                .map(mapper::toResponse)
                .getContent();

        return new AgentHealthSummaryResponse(
                agentId,
                agent.getHealthStatus(),
                agent.getLastHealthCheckAt(),
                availability,
                properties.historyWindowDays(),
                total,
                successful,
                recent);
    }

    @Transactional(readOnly = true)
    public PageResponse<AgentHealthCheckResponse> getHistory(UUID actorId, UUID organizationId, UUID projectId,
                                                             UUID agentId, Pageable pageable) {
        accessGuard.requireReadable(organizationId, projectId, agentId, actorId);
        return PageResponse.from(
                healthCheckRepository.findByAgentIdOrderByCheckedAtDesc(agentId, pageable), mapper::toResponse);
    }
}
