package com.broksforge.modules.evaluation.service;

import com.broksforge.common.exception.ResourceNotFoundException;
import com.broksforge.modules.evaluation.domain.EvaluationJob;
import com.broksforge.modules.evaluation.domain.EvaluationProfile;
import com.broksforge.modules.evaluation.repository.EvaluationJobRepository;
import com.broksforge.modules.evaluation.repository.EvaluationProfileRepository;
import com.broksforge.modules.organization.domain.OrganizationRole;
import com.broksforge.modules.organization.service.OrganizationAccessService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Single enforcement point for evaluation job/profile authorization and tenant
 * isolation, loading by the {@code (id, projectId, organizationId)} tuple for IDOR
 * safety.
 */
@Component
public class EvaluationAccessGuard {

    private final EvaluationJobRepository jobRepository;
    private final EvaluationProfileRepository profileRepository;
    private final OrganizationAccessService accessService;

    public EvaluationAccessGuard(EvaluationJobRepository jobRepository,
                                 EvaluationProfileRepository profileRepository,
                                 OrganizationAccessService accessService) {
        this.jobRepository = jobRepository;
        this.profileRepository = profileRepository;
        this.accessService = accessService;
    }

    @Transactional(readOnly = true)
    public EvaluationJob requireReadableJob(UUID organizationId, UUID projectId, UUID jobId, UUID actorId) {
        accessService.requireMembership(organizationId, actorId);
        return loadJob(organizationId, projectId, jobId);
    }

    @Transactional(readOnly = true)
    public EvaluationJob requireManageableJob(UUID organizationId, UUID projectId, UUID jobId, UUID actorId,
                                              OrganizationRole minRole) {
        accessService.requireRole(organizationId, actorId, minRole);
        return loadJob(organizationId, projectId, jobId);
    }

    @Transactional(readOnly = true)
    public EvaluationProfile requireReadableProfile(UUID organizationId, UUID projectId, UUID profileId, UUID actorId) {
        accessService.requireMembership(organizationId, actorId);
        return loadProfile(organizationId, projectId, profileId);
    }

    @Transactional(readOnly = true)
    public EvaluationProfile requireManageableProfile(UUID organizationId, UUID projectId, UUID profileId,
                                                      UUID actorId, OrganizationRole minRole) {
        accessService.requireRole(organizationId, actorId, minRole);
        return loadProfile(organizationId, projectId, profileId);
    }

    private EvaluationJob loadJob(UUID organizationId, UUID projectId, UUID jobId) {
        return jobRepository.findByIdAndProjectIdAndOrganizationIdAndDeletedFalse(jobId, projectId, organizationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Evaluation job", jobId));
    }

    private EvaluationProfile loadProfile(UUID organizationId, UUID projectId, UUID profileId) {
        return profileRepository
                .findByIdAndProjectIdAndOrganizationIdAndDeletedFalse(profileId, projectId, organizationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Evaluation profile", profileId));
    }
}
