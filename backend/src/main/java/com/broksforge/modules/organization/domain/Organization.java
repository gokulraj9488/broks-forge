package com.broksforge.modules.organization.domain;

import com.broksforge.common.domain.SoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * A tenant boundary. Users join organizations as members with a role, and
 * projects belong to organizations.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "organizations",
        uniqueConstraints = @UniqueConstraint(name = "uq_organizations_slug", columnNames = "slug"),
        indexes = {
                @Index(name = "idx_organizations_slug", columnList = "slug"),
                @Index(name = "idx_organizations_owner", columnList = "owner_id")
        }
)
public class Organization extends SoftDeletableEntity {

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "slug", nullable = false, length = 64)
    private String slug;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private OrganizationStatus status = OrganizationStatus.ACTIVE;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;
}
