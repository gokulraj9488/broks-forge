package com.broksforge.modules.evaluation.repository;

import com.broksforge.modules.evaluation.domain.EvaluationJob;
import com.broksforge.modules.evaluation.domain.EvaluationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EvaluationJobRepository
        extends JpaRepository<EvaluationJob, UUID>, JpaSpecificationExecutor<EvaluationJob> {

    Optional<EvaluationJob> findByIdAndProjectIdAndOrganizationIdAndDeletedFalse(
            UUID id, UUID projectId, UUID organizationId);

    long countByProjectIdAndDeletedFalse(UUID projectId);

    long countByProjectIdAndStatusAndDeletedFalse(UUID projectId, EvaluationStatus status);

    List<EvaluationJob> findTop10ByProjectIdAndDeletedFalseOrderByCreatedAtDesc(UUID projectId);

    List<EvaluationJob> findByProjectIdAndStatusAndDeletedFalseOrderByCreatedAtDesc(
            UUID projectId, EvaluationStatus status);
}
