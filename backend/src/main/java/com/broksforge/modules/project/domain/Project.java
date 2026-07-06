package com.broksforge.modules.project.domain;

import com.broksforge.common.domain.SoftDeletableEntity;
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
 * A project within an organization. Slugs are unique per organization.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
// (organization_id, slug) uniqueness is enforced by a PARTIAL unique index
// (WHERE deleted = false) defined in migration V30, so a slug frees up once its
// project is soft-deleted. JPA's @UniqueConstraint cannot express a partial index,
// so it is intentionally omitted here — the migration is the source of truth.
@Table(
        name = "projects",
        indexes = {
                @Index(name = "idx_projects_org", columnList = "organization_id"),
                @Index(name = "idx_projects_status", columnList = "status")
        }
)
public class Project extends SoftDeletableEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "slug", nullable = false, length = 64)
    private String slug;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ProjectStatus status = ProjectStatus.ACTIVE;
}
