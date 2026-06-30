package com.broksforge.modules.agent.service;

import com.broksforge.common.exception.BadRequestException;
import com.broksforge.common.exception.ErrorCode;
import com.broksforge.common.exception.ResourceConflictException;
import com.broksforge.common.exception.ResourceNotFoundException;
import com.broksforge.common.web.PageResponse;
import com.broksforge.modules.agent.domain.Agent;
import com.broksforge.modules.agent.domain.AgentVersion;
import com.broksforge.modules.agent.repository.AgentVersionRepository;
import com.broksforge.modules.agent.web.AgentVersionMapper;
import com.broksforge.modules.agent.web.dto.AgentVersionResponse;
import com.broksforge.modules.agent.web.dto.RegisterAgentVersionRequest;
import com.broksforge.modules.organization.domain.OrganizationRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Manages agent versions (deployments) and the activation / rollback flow.
 * Exactly one version is active per agent; activation atomically deactivates the
 * previous active version and points the agent at the new one.
 */
@Slf4j
@Service
public class AgentVersionService {

    private final AgentVersionRepository versionRepository;
    private final AgentAccessGuard accessGuard;
    private final AgentVersionMapper mapper;

    public AgentVersionService(AgentVersionRepository versionRepository,
                               AgentAccessGuard accessGuard,
                               AgentVersionMapper mapper) {
        this.versionRepository = versionRepository;
        this.accessGuard = accessGuard;
        this.mapper = mapper;
    }

    @Transactional
    public AgentVersionResponse register(UUID actorId, UUID organizationId, UUID projectId, UUID agentId,
                                         RegisterAgentVersionRequest request) {
        Agent agent = accessGuard.requireManageable(organizationId, projectId, agentId, actorId,
                OrganizationRole.MEMBER);
        accessGuard.ensureNotArchived(agent);

        if (versionRepository.existsByAgentIdAndVersionNumberIgnoreCase(agentId, request.versionNumber().trim())) {
            throw new ResourceConflictException(ErrorCode.AGENT_VERSION_ALREADY_EXISTS,
                    "A version with this number already exists for this agent");
        }

        long nextSequence = versionRepository.findFirstByAgentIdOrderBySequenceDesc(agentId)
                .map(v -> v.getSequence() + 1)
                .orElse(1L);

        AgentVersion version = new AgentVersion();
        version.setAgentId(agentId);
        version.setOrganizationId(organizationId);
        version.setProjectId(projectId);
        version.setVersionNumber(request.versionNumber().trim());
        version.setSequence(nextSequence);
        version.setModel(request.model().trim());
        version.setProvider(request.provider());
        version.setFrameworkVersion(request.frameworkVersion());
        version.setGitCommitSha(request.gitCommitSha());
        version.setPromptVersion(request.promptVersion());
        version.setEnvironment(request.environment());
        version.setReleaseNotes(request.releaseNotes());
        version.setDeploymentTimestamp(Instant.now());
        version.setRollbackReady(request.rollbackReady() == null || request.rollbackReady());

        AgentVersion saved = versionRepository.save(version);
        log.info("Agent {} version {} (#{}) registered by {}", agentId, saved.getVersionNumber(), nextSequence, actorId);

        if (Boolean.TRUE.equals(request.activate())) {
            activateInternal(agent, saved);
        }
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<AgentVersionResponse> list(UUID actorId, UUID organizationId, UUID projectId, UUID agentId,
                                                   Pageable pageable) {
        accessGuard.requireReadable(organizationId, projectId, agentId, actorId);
        return PageResponse.from(versionRepository.findByAgentId(agentId, pageable), mapper::toResponse);
    }

    @Transactional
    public AgentVersionResponse activate(UUID actorId, UUID organizationId, UUID projectId, UUID agentId,
                                         UUID versionId) {
        Agent agent = accessGuard.requireManageable(organizationId, projectId, agentId, actorId,
                OrganizationRole.MEMBER);
        accessGuard.ensureNotArchived(agent);
        AgentVersion version = getVersionOrThrow(agentId, versionId);

        activateInternal(agent, version);
        log.info("Agent {} activated version {} by {}", agentId, version.getVersionNumber(), actorId);
        return mapper.toResponse(version);
    }

    @Transactional
    public AgentVersionResponse rollback(UUID actorId, UUID organizationId, UUID projectId, UUID agentId,
                                         UUID versionId) {
        Agent agent = accessGuard.requireManageable(organizationId, projectId, agentId, actorId,
                OrganizationRole.MEMBER);
        accessGuard.ensureNotArchived(agent);
        AgentVersion version = getVersionOrThrow(agentId, versionId);

        if (!version.isRollbackReady()) {
            throw new BadRequestException("This version is not marked rollback-ready");
        }
        activateInternal(agent, version);
        log.warn("Agent {} rolled back to version {} by {}", agentId, version.getVersionNumber(), actorId);
        return mapper.toResponse(version);
    }

    private void activateInternal(Agent agent, AgentVersion target) {
        List<AgentVersion> currentlyActive = versionRepository.findByAgentIdAndActiveTrue(agent.getId());
        for (AgentVersion active : currentlyActive) {
            if (!active.getId().equals(target.getId())) {
                active.setActive(false);
            }
        }
        target.setActive(true);
        agent.setCurrentActiveVersionId(target.getId());
    }

    private AgentVersion getVersionOrThrow(UUID agentId, UUID versionId) {
        return versionRepository.findByIdAndAgentId(versionId, agentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Agent version", versionId));
    }
}
