package com.broksforge.modules.dataset.service;

import com.broksforge.common.exception.ErrorCode;
import com.broksforge.common.exception.ResourceConflictException;
import com.broksforge.common.exception.ResourceNotFoundException;
import com.broksforge.modules.dataset.domain.Dataset;
import com.broksforge.modules.dataset.repository.DatasetRepository;
import com.broksforge.modules.organization.domain.OrganizationRole;
import com.broksforge.modules.organization.service.OrganizationAccessService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Single enforcement point for dataset authorization and tenant isolation. Loads
 * by the {@code (id, projectId, organizationId)} tuple so a dataset id from another
 * project/organization resolves to 404 (IDOR-safe), mirroring {@code AgentAccessGuard}.
 */
@Component
public class DatasetAccessGuard {

    private final DatasetRepository datasetRepository;
    private final OrganizationAccessService accessService;

    public DatasetAccessGuard(DatasetRepository datasetRepository, OrganizationAccessService accessService) {
        this.datasetRepository = datasetRepository;
        this.accessService = accessService;
    }

    @Transactional(readOnly = true)
    public Dataset requireReadable(UUID organizationId, UUID projectId, UUID datasetId, UUID actorId) {
        accessService.requireMembership(organizationId, actorId);
        return loadOrThrow(organizationId, projectId, datasetId);
    }

    @Transactional(readOnly = true)
    public Dataset requireManageable(UUID organizationId, UUID projectId, UUID datasetId, UUID actorId,
                                     OrganizationRole minRole) {
        accessService.requireRole(organizationId, actorId, minRole);
        return loadOrThrow(organizationId, projectId, datasetId);
    }

    public void ensureNotArchived(Dataset dataset) {
        if (dataset.isArchived()) {
            throw new ResourceConflictException(ErrorCode.AGENT_ARCHIVED,
                    "Dataset is archived; unarchive it before making changes");
        }
    }

    private Dataset loadOrThrow(UUID organizationId, UUID projectId, UUID datasetId) {
        return datasetRepository
                .findByIdAndProjectIdAndOrganizationIdAndDeletedFalse(datasetId, projectId, organizationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Dataset", datasetId));
    }
}
