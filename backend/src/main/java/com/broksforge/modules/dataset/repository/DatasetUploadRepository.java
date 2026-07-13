package com.broksforge.modules.dataset.repository;

import com.broksforge.modules.dataset.domain.DatasetUpload;
import com.broksforge.modules.dataset.domain.DatasetUploadStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DatasetUploadRepository extends JpaRepository<DatasetUpload, UUID> {

    Optional<DatasetUpload> findByIdAndDatasetId(UUID id, UUID datasetId);

    Page<DatasetUpload> findByDatasetIdOrderByCreatedAtDesc(UUID datasetId, Pageable pageable);

    /** Duplicate-content detection: an earlier upload with the same bytes already produced a version. */
    Optional<DatasetUpload> findFirstByDatasetIdAndChecksumAndStatusInOrderByCreatedAtDesc(
            UUID datasetId, String checksum, List<DatasetUploadStatus> statuses);
}
