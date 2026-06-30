package com.broksforge.modules.agent.service;

import com.broksforge.modules.agent.domain.Agent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Published bridge that lets other modules invoke an agent <em>by id</em> without
 * reaching into the agent aggregate. Resolves the agent (with the standard access
 * guard, so tenant isolation and IDOR protection apply) and its decrypted auth
 * headers into an {@link AgentInvocationTarget}.
 */
@Service
public class AgentInvocationService {

    private final AgentAccessGuard accessGuard;
    private final AgentCredentialService credentialService;

    public AgentInvocationService(AgentAccessGuard accessGuard, AgentCredentialService credentialService) {
        this.accessGuard = accessGuard;
        this.credentialService = credentialService;
    }

    @Transactional(readOnly = true)
    public AgentInvocationTarget resolveTarget(UUID actorId, UUID organizationId, UUID projectId, UUID agentId) {
        Agent agent = accessGuard.requireReadable(organizationId, projectId, agentId, actorId);
        return new AgentInvocationTarget(
                agent.getId(),
                agent.getCurrentActiveVersionId(),
                agent.getEndpointUrl(),
                credentialService.resolveAuthHeaders(agent));
    }
}
