package com.broksforge.modules.agent.service;

import com.broksforge.common.exception.ErrorCode;
import com.broksforge.common.exception.ResourceConflictException;
import com.broksforge.common.exception.ResourceNotFoundException;
import com.broksforge.modules.agent.domain.Agent;
import com.broksforge.modules.agent.repository.AgentRepository;
import com.broksforge.modules.organization.domain.OrganizationRole;
import com.broksforge.modules.organization.service.OrganizationAccessService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Single enforcement point for agent-scoped authorization and tenant isolation.
 *
 * <p>Every agent sub-operation resolves its target through this guard, which both
 * checks the caller's organization role and loads the agent by the full
 * {@code (id, projectId, organizationId)} tuple. Loading by the compound key
 * prevents IDOR: an agent id from another project/organization simply resolves to
 * 404. Centralising this keeps the rule in exactly one place across the agent,
 * version, credential and health services.</p>
 */
@Component
public class AgentAccessGuard {

    private final AgentRepository agentRepository;
    private final OrganizationAccessService accessService;

    public AgentAccessGuard(AgentRepository agentRepository, OrganizationAccessService accessService) {
        this.agentRepository = agentRepository;
        this.accessService = accessService;
    }

    /**
     * Resolves an agent for a read operation, requiring organization membership.
     */
    @Transactional(readOnly = true)
    public Agent requireReadable(UUID organizationId, UUID projectId, UUID agentId, UUID actorId) {
        accessService.requireMembership(organizationId, actorId);
        return loadOrThrow(organizationId, projectId, agentId);
    }

    /**
     * Resolves an agent for a mutating operation, requiring at least {@code minRole}.
     */
    @Transactional(readOnly = true)
    public Agent requireManageable(UUID organizationId, UUID projectId, UUID agentId, UUID actorId,
                                   OrganizationRole minRole) {
        accessService.requireRole(organizationId, actorId, minRole);
        return loadOrThrow(organizationId, projectId, agentId);
    }

    /**
     * Guards mutating operations that are not permitted on archived agents.
     */
    public void ensureNotArchived(Agent agent) {
        if (agent.isArchived()) {
            throw new ResourceConflictException(ErrorCode.AGENT_ARCHIVED,
                    "Agent is archived; unarchive it before making changes");
        }
    }

    private Agent loadOrThrow(UUID organizationId, UUID projectId, UUID agentId) {
        return agentRepository
                .findByIdAndProjectIdAndOrganizationIdAndDeletedFalse(agentId, projectId, organizationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Agent", agentId));
    }
}
