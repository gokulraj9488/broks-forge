package com.broksforge.modules.prompt.service;

import com.broksforge.common.exception.ErrorCode;
import com.broksforge.common.exception.ResourceConflictException;
import com.broksforge.common.exception.ResourceNotFoundException;
import com.broksforge.modules.organization.domain.OrganizationRole;
import com.broksforge.modules.organization.service.OrganizationAccessService;
import com.broksforge.modules.prompt.domain.Prompt;
import com.broksforge.modules.prompt.repository.PromptRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Single enforcement point for prompt authorization and tenant isolation, loading
 * by the {@code (id, projectId, organizationId)} tuple for IDOR safety.
 */
@Component
public class PromptAccessGuard {

    private final PromptRepository promptRepository;
    private final OrganizationAccessService accessService;

    public PromptAccessGuard(PromptRepository promptRepository, OrganizationAccessService accessService) {
        this.promptRepository = promptRepository;
        this.accessService = accessService;
    }

    @Transactional(readOnly = true)
    public Prompt requireReadable(UUID organizationId, UUID projectId, UUID promptId, UUID actorId) {
        accessService.requireMembership(organizationId, actorId);
        return loadOrThrow(organizationId, projectId, promptId);
    }

    @Transactional(readOnly = true)
    public Prompt requireManageable(UUID organizationId, UUID projectId, UUID promptId, UUID actorId,
                                    OrganizationRole minRole) {
        accessService.requireRole(organizationId, actorId, minRole);
        return loadOrThrow(organizationId, projectId, promptId);
    }

    public void ensureNotArchived(Prompt prompt) {
        if (prompt.isArchived()) {
            throw new ResourceConflictException(ErrorCode.AGENT_ARCHIVED,
                    "Prompt is archived; unarchive it before making changes");
        }
    }

    private Prompt loadOrThrow(UUID organizationId, UUID projectId, UUID promptId) {
        return promptRepository
                .findByIdAndProjectIdAndOrganizationIdAndDeletedFalse(promptId, projectId, organizationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Prompt", promptId));
    }
}
