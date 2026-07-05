package com.broksforge.modules.auth.domain;

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
 * Single-use token confirming a password change by e-mail. Issued only after
 * the user has re-entered their current password; the change itself is applied
 * when the emailed link is confirmed. Only the SHA-256 hash is persisted.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "password_change_tokens",
        uniqueConstraints = @UniqueConstraint(name = "uq_password_change_tokens_hash", columnNames = "token_hash"),
        indexes = @Index(name = "idx_password_change_tokens_user", columnList = "user_id")
)
public class PasswordChangeToken extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    public boolean isUsable() {
        return usedAt == null && expiresAt.isAfter(Instant.now());
    }

    public void markUsed() {
        this.usedAt = Instant.now();
    }
}
