package com.broksforge.modules.dataset.domain;

import com.broksforge.common.domain.BaseEntity;
import com.broksforge.common.persistence.JsonMetadataConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A single row of a {@link DatasetVersion}: an input, an optional expected output
 * (ground truth for reference-based metrics), and arbitrary metadata carried over
 * from the source columns. Items are immutable once their version is created.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "dataset_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_dataset_items_sequence", columnNames = {"dataset_version_id", "sequence"}),
        indexes = {
                @Index(name = "idx_dataset_items_version", columnList = "dataset_version_id"),
                @Index(name = "idx_dataset_items_dataset", columnList = "dataset_id")
        }
)
public class DatasetItem extends BaseEntity {

    @Column(name = "dataset_version_id", nullable = false)
    private UUID datasetVersionId;

    @Column(name = "dataset_id", nullable = false)
    private UUID datasetId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    /** Position within the version; drives default ordering. */
    @Column(name = "sequence", nullable = false)
    private int sequence;

    @Column(name = "input", nullable = false, columnDefinition = "text")
    private String input;

    @Column(name = "expected_output", columnDefinition = "text")
    private String expectedOutput;

    @Convert(converter = JsonMetadataConverter.class)
    @Column(name = "metadata", columnDefinition = "text")
    private Map<String, Object> metadata = new LinkedHashMap<>();
}
