package com.broksforge.modules.dataset.repository;

import com.broksforge.modules.dataset.domain.Dataset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DatasetRepository extends JpaRepository<Dataset, UUID>, JpaSpecificationExecutor<Dataset> {

    Optional<Dataset> findByIdAndProjectIdAndOrganizationIdAndDeletedFalse(UUID id, UUID projectId, UUID organizationId);

    boolean existsByProjectIdAndSlugIgnoreCaseAndDeletedFalse(UUID projectId, String slug);

    long countByProjectIdAndDeletedFalse(UUID projectId);
}
