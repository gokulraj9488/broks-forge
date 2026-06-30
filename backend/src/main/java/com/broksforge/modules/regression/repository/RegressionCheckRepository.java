package com.broksforge.modules.regression.repository;

import com.broksforge.modules.regression.domain.RegressionCheck;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RegressionCheckRepository extends JpaRepository<RegressionCheck, UUID> {

    Optional<RegressionCheck> findByIdAndProjectIdAndOrganizationIdAndDeletedFalse(
            UUID id, UUID projectId, UUID organizationId);

    Page<RegressionCheck> findByProjectIdAndDeletedFalseOrderByCreatedAtDesc(UUID projectId, Pageable pageable);

    List<RegressionCheck> findTop5ByProjectIdAndRegressedTrueAndDeletedFalseOrderByCreatedAtDesc(UUID projectId);
}
