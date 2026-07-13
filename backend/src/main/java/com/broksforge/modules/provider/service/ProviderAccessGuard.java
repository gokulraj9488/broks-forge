package com.broksforge.modules.provider.service;

import com.broksforge.common.exception.ResourceNotFoundException;
import com.broksforge.modules.organization.domain.OrganizationRole;
import com.broksforge.modules.organization.service.OrganizationAccessService;
import com.broksforge.modules.provider.domain.Provider;
import com.broksforge.modules.provider.repository.ProviderRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Single enforcement point for provider-scoped authorization and tenant isolation, mirroring
 * {@code AgentAccessGuard}: loads by the full {@code (id, projectId, organizationId)} tuple so a
 * provider id from another project/organization resolves to 404, not another tenant's config.
 */
@Component
public class ProviderAccessGuard {

    private final ProviderRepository providerRepository;
    private final OrganizationAccessService accessService;

    public ProviderAccessGuard(ProviderRepository providerRepository, OrganizationAccessService accessService) {
        this.providerRepository = providerRepository;
        this.accessService = accessService;
    }

    @Transactional(readOnly = true)
    public Provider requireReadable(UUID organizationId, UUID projectId, UUID providerId, UUID actorId) {
        accessService.requireMembership(organizationId, actorId);
        return loadOrThrow(organizationId, projectId, providerId);
    }

    @Transactional(readOnly = true)
    public Provider requireManageable(UUID organizationId, UUID projectId, UUID providerId, UUID actorId,
                                      OrganizationRole minRole) {
        accessService.requireRole(organizationId, actorId, minRole);
        return loadOrThrow(organizationId, projectId, providerId);
    }

    private Provider loadOrThrow(UUID organizationId, UUID projectId, UUID providerId) {
        return providerRepository
                .findByIdAndProjectIdAndOrganizationIdAndDeletedFalse(providerId, projectId, organizationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Provider", providerId));
    }
}
