package com.broksforge.modules.evaluation.repository;

import com.broksforge.modules.evaluation.domain.EvaluationProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EvaluationProfileRepository extends JpaRepository<EvaluationProfile, UUID> {

    Optional<EvaluationProfile> findByIdAndProjectIdAndOrganizationIdAndDeletedFalse(
            UUID id, UUID projectId, UUID organizationId);

    boolean existsByProjectIdAndSlugIgnoreCaseAndDeletedFalse(UUID projectId, String slug);

    Page<EvaluationProfile> findByProjectIdAndDeletedFalseOrderByCreatedAtDesc(UUID projectId, Pageable pageable);

    @Query("SELECT p FROM EvaluationProfile p WHERE p.projectId = :projectId AND p.deleted = false "
            + "AND (:search IS NULL OR LOWER(p.name) LIKE CONCAT('%', CAST(:search AS string), '%') "
            + "OR LOWER(p.slug) LIKE CONCAT('%', CAST(:search AS string), '%'))")
    Page<EvaluationProfile> search(@Param("projectId") UUID projectId, @Param("search") String search, Pageable pageable);
}
