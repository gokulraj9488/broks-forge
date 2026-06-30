package com.broksforge.modules.dataset.repository;

import com.broksforge.modules.dataset.domain.DatasetVersion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DatasetVersionRepository extends JpaRepository<DatasetVersion, UUID> {

    Page<DatasetVersion> findByDatasetIdOrderByVersionNumberDesc(UUID datasetId, Pageable pageable);

    Optional<DatasetVersion> findByIdAndDatasetId(UUID id, UUID datasetId);

    Optional<DatasetVersion> findFirstByDatasetIdOrderByVersionNumberDesc(UUID datasetId);
}
