package com.broksforge.modules.dataset.domain;

import com.broksforge.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * An audit record of one file-upload attempt against a dataset (CSV/JSON/XLSX/ZIP),
 * distinct from the pre-existing paste-mode import ({@code ImportDatasetRequest}), which
 * does not create these rows. On success, {@link #datasetVersionId} points at the
 * immutable {@link DatasetVersion} the upload produced; on failure or duplicate, no
 * version is created. Never soft-deleted — this is an append-only history, like
 * {@code AgentHealthCheck}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "dataset_uploads",
        indexes = {
                @Index(name = "idx_dataset_uploads_dataset", columnList = "dataset_id"),
                @Index(name = "idx_dataset_uploads_dataset_checksum", columnList = "dataset_id, checksum")
        }
)
public class DatasetUpload extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "dataset_id", nullable = false)
    private UUID datasetId;

    @Column(name = "filename", nullable = false, length = 255)
    private String filename;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false, length = 32)
    private DatasetSourceFormat format;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "checksum", nullable = false, length = 64)
    private String checksum;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private DatasetUploadStatus status = DatasetUploadStatus.PENDING;

    @Column(name = "row_count")
    private Integer rowCount;

    @Column(name = "column_count")
    private Integer columnCount;

    @Column(name = "dataset_version_id")
    private UUID datasetVersionId;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;
}
