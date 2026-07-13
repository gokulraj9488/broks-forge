package com.broksforge.modules.agent.service;

import com.broksforge.common.exception.ErrorCode;
import com.broksforge.common.exception.ResourceConflictException;
import com.broksforge.modules.agent.domain.Agent;
import com.broksforge.modules.agent.domain.AgentVersion;
import com.broksforge.modules.agent.repository.AgentVersionRepository;
import com.broksforge.modules.provider.domain.Provider;
import com.broksforge.modules.provider.repository.ProviderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Published bridge that lets other modules invoke an agent <em>by id</em> without
 * reaching into the agent aggregate. Resolves the agent (with the standard access
 * guard, so tenant isolation and IDOR protection apply), its decrypted auth
 * headers, and its active version's model (if any) into an {@link AgentInvocationTarget}.
 *
 * <p>When the agent is linked to a Provider (Provider abstraction milestone), this is also the
 * single enforcement point for that provider being enabled — a disabled provider rejects
 * resolution with a clear error, before any request is ever built, rather than failing deep
 * inside the HTTP call. Resolution also stamps the provider's {@code lastUsedAt}, since this
 * method is called once per job/resume (not once per row — see
 * {@code EvaluationBackgroundRunner}), making it a cheap, accurate proxy for "this provider was
 * just used" without adding a write to the per-row hot path.</p>
 */
@Service
public class AgentInvocationService {

    private final AgentAccessGuard accessGuard;
    private final AgentCredentialService credentialService;
    private final AgentVersionRepository versionRepository;
    private final ProviderRepository providerRepository;

    public AgentInvocationService(AgentAccessGuard accessGuard, AgentCredentialService credentialService,
                                  AgentVersionRepository versionRepository, ProviderRepository providerRepository) {
        this.accessGuard = accessGuard;
        this.credentialService = credentialService;
        this.versionRepository = versionRepository;
        this.providerRepository = providerRepository;
    }

    @Transactional
    public AgentInvocationTarget resolveTarget(UUID actorId, UUID organizationId, UUID projectId, UUID agentId) {
        Agent agent = accessGuard.requireReadable(organizationId, projectId, agentId, actorId);
        String model = agent.getCurrentActiveVersionId() == null ? null
                : versionRepository.findByIdAndAgentId(agent.getCurrentActiveVersionId(), agentId)
                        .map(AgentVersion::getModel)
                        .orElse(null);

        String providerDefaultModel = null;
        if (agent.getProviderId() != null) {
            Provider provider = providerRepository
                    .findByIdAndProjectIdAndOrganizationIdAndDeletedFalse(agent.getProviderId(), projectId,
                            organizationId)
                    .orElse(null);
            if (provider != null) {
                if (!provider.isEnabled()) {
                    throw new ResourceConflictException(ErrorCode.PROVIDER_DISABLED,
                            "This agent's provider ('" + provider.getName() + "') is disabled; "
                                    + "re-enable it or unlink the agent before invoking it");
                }
                provider.recordUsage(Instant.now());
                providerDefaultModel = provider.getDefaultModel();
            }
        }

        return new AgentInvocationTarget(
                agent.getId(),
                agent.getCurrentActiveVersionId(),
                agent.getEndpointUrl(),
                credentialService.resolveAuthHeaders(agent),
                model,
                providerDefaultModel);
    }
}
