package com.broksforge.modules.auth.domain;

import com.broksforge.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A single-use, time-boxed e-mail OTP that authorises a password change
 * (see ADR 0017). Issued only after the user re-enters their current password.
 *
 * <p>Only the SHA-256 hash of the 6-digit code is persisted — never the code.
 * Because a 6-digit code is low-entropy, {@link #attempts} caps online guessing;
 * once the code is verified, a high-entropy single-use {@link #ticketHash}
 * authorises the final set-password step so the new password is never submitted
 * together with the code.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "password_change_otps",
        indexes = {
                @Index(name = "idx_password_change_otps_user", columnList = "user_id"),
                @Index(name = "idx_password_change_otps_ticket", columnList = "ticket_hash")
        }
)
public class PasswordChangeOtp extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 5;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "ticket_hash", length = 64)
    private String ticketHash;

    @Column(name = "ticket_expires_at")
    private Instant ticketExpiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    /** The code can still be checked: not verified, not consumed, unexpired, attempts remain. */
    public boolean isCodeVerifiable() {
        return verifiedAt == null && consumedAt == null
                && expiresAt.isAfter(Instant.now())
                && attempts < maxAttempts;
    }

    public boolean hasAttemptsRemaining() {
        return attempts < maxAttempts;
    }

    public void recordFailedAttempt() {
        this.attempts++;
    }

    /** Marks the code verified and attaches the single-use continuation ticket. */
    public void markVerified(String ticketHash, Instant ticketExpiresAt) {
        this.verifiedAt = Instant.now();
        this.ticketHash = ticketHash;
        this.ticketExpiresAt = ticketExpiresAt;
    }

    /** The verification ticket can still be used: verified, not consumed, unexpired. */
    public boolean isTicketUsable() {
        return verifiedAt != null && consumedAt == null
                && ticketExpiresAt != null && ticketExpiresAt.isAfter(Instant.now());
    }

    public void markConsumed() {
        this.consumedAt = Instant.now();
    }
}
