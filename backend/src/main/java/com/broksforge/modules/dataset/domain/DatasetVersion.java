package com.broksforge.modules.dataset.domain;

import com.broksforge.common.domain.BaseEntity;
import com.broksforge.common.persistence.JsonStringListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An immutable snapshot of a dataset's rows. Once created, a version's items and
 * metadata never change; importing more data produces a new version. This is what
 * makes an evaluation reproducible — a job records the exact {@code datasetVersionId}
 * it ran against.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "dataset_versions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_dataset_versions_number", columnNames = {"dataset_id", "version_number"}),
        indexes = {
                @Index(name = "idx_dataset_versions_dataset", columnList = "dataset_id"),
                @Index(name = "idx_dataset_versions_org", columnList = "organization_id")
        }
)
public class DatasetVersion extends BaseEntity {

    @Column(name = "dataset_id", nullable = false)
    private UUID datasetId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_format", nullable = false, length = 32)
    private DatasetSourceFormat sourceFormat;

    @Column(name = "item_count", nullable = false)
    private int itemCount = 0;

    /** Column names captured at import time (CSV header / JSON keys). */
    @Convert(converter = JsonStringListConverter.class)
    @Column(name = "columns", columnDefinition = "text")
    private List<String> columns = new ArrayList<>();

    /** SHA-256 (hex) of the imported source content; lets identical imports be spotted. */
    @Column(name = "checksum", length = 64)
    private String checksum;
}
