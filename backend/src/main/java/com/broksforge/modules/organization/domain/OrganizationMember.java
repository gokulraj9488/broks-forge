package com.broksforge.modules.organization.domain;

import com.broksforge.common.domain.BaseEntity;
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

import java.time.Instant;
import java.util.UUID;

/**
 * Membership of a {@code User} in an {@code Organization}, carrying the user's
 * organization-scoped role. The {@code (organization_id, user_id)} pair is
 * unique.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "organization_members",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_org_members_org_user", columnNames = {"organization_id", "user_id"}),
        indexes = {
                @Index(name = "idx_org_members_org", columnList = "organization_id"),
                @Index(name = "idx_org_members_user", columnList = "user_id")
        }
)
public class OrganizationMember extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private OrganizationRole role = OrganizationRole.MEMBER;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt = Instant.now();
}
