package com.broksforge.modules.evaluation.repository;

import com.broksforge.modules.evaluation.domain.EvaluationProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EvaluationProfileRepository extends JpaRepository<EvaluationProfile, UUID> {

    Optional<EvaluationProfile> findByIdAndProjectIdAndOrganizationIdAndDeletedFalse(
            UUID id, UUID projectId, UUID organizationId);

    boolean existsByProjectIdAndSlugIgnoreCaseAndDeletedFalse(UUID projectId, String slug);

    Page<EvaluationProfile> findByProjectIdAndDeletedFalseOrderByCreatedAtDesc(UUID projectId, Pageable pageable);
}
