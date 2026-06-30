package com.broksforge.modules.apikey.domain;

import com.broksforge.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A programmatic API key scoped to a project.
 *
 * <p>The full key is shown to the user exactly once at creation. Only two
 * things are persisted: the public {@code keyPrefix} (used to locate the row)
 * and the SHA-256 {@code secretHash} of the secret portion. The secret itself
 * is never stored.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "api_keys",
        uniqueConstraints = @UniqueConstraint(name = "uq_api_keys_prefix", columnNames = "key_prefix"),
        indexes = {
                @Index(name = "idx_api_keys_project", columnList = "project_id"),
                @Index(name = "idx_api_keys_org", columnList = "organization_id"),
                @Index(name = "idx_api_keys_prefix", columnList = "key_prefix")
        }
)
public class ApiKey extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    /** Public, displayable identifier, e.g. {@code bf_a1b2c3d4e5f6}. Unique. */
    @Column(name = "key_prefix", nullable = false, length = 32)
    private String keyPrefix;

    /** SHA-256 hex digest of the secret portion. */
    @Column(name = "secret_hash", nullable = false, length = 64)
    private String secretHash;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked", nullable = false)
    private boolean revoked = false;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public boolean isActive() {
        return !revoked && (expiresAt == null || expiresAt.isAfter(Instant.now()));
    }

    public void revoke() {
        if (!revoked) {
            this.revoked = true;
            this.revokedAt = Instant.now();
        }
    }
}
