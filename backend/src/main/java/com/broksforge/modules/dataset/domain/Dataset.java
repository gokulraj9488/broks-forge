package com.broksforge.modules.dataset.domain;

import com.broksforge.common.domain.SoftDeletableEntity;
import com.broksforge.common.persistence.JsonStringListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A named collection of evaluation data within a project. The dataset itself is a
 * stable container; its actual rows live in immutable {@link DatasetVersion}
 * snapshots so an evaluation job can pin an exact version and remain reproducible
 * (see ADR 0007). The current version pointer and a denormalised item count keep
 * list views cheap.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
// (project_id, slug) uniqueness is enforced by a PARTIAL unique index
// (WHERE deleted = false) defined in migration V30, so a slug frees up once its
// dataset is soft-deleted. JPA's @UniqueConstraint cannot express a partial index,
// so it is intentionally omitted here — the migration is the source of truth.
@Table(
        name = "datasets",
        indexes = {
                @Index(name = "idx_datasets_project", columnList = "project_id"),
                @Index(name = "idx_datasets_org", columnList = "organization_id"),
                @Index(name = "idx_datasets_owner", columnList = "owner_id"),
                @Index(name = "idx_datasets_status", columnList = "status")
        }
)
public class Dataset extends SoftDeletableEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "slug", nullable = false, length = 64)
    private String slug;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 32)
    private DatasetVisibility visibility = DatasetVisibility.PRIVATE;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private DatasetStatus status = DatasetStatus.ACTIVE;

    @Convert(converter = JsonStringListConverter.class)
    @Column(name = "tags", columnDefinition = "text")
    private List<String> tags = new ArrayList<>();

    @Column(name = "latest_version_number", nullable = false)
    private int latestVersionNumber = 0;

    @Column(name = "current_version_id")
    private UUID currentVersionId;

    /** Denormalised item count of the current version; avoids an N+1 on listings. */
    @Column(name = "current_item_count", nullable = false)
    private int currentItemCount = 0;

    public boolean isArchived() {
        return status == DatasetStatus.ARCHIVED;
    }

    public void archive() {
        this.status = DatasetStatus.ARCHIVED;
    }

    public void unarchive() {
        this.status = DatasetStatus.ACTIVE;
    }
}
