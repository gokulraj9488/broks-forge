-- ===========================================================================
-- V27 — Password-change OTP flow
-- ---------------------------------------------------------------------------
-- Supersedes the emailed-link password change (V26 password_change_tokens,
-- retained) with a short numeric OTP verified in-session (see ADR 0017).
--
-- Only the SHA-256 hash of the 6-digit code is stored (never the code itself).
-- Because a 6-digit code is low-entropy, the row carries an attempt counter so
-- online guessing is capped; once the code is verified a high-entropy, single-
-- use ticket (also stored hashed) authorises the final set-password step.
-- ===========================================================================

CREATE TABLE password_change_otps (
    id                 UUID          NOT NULL DEFAULT gen_random_uuid(),
    version            BIGINT        NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ   NOT NULL,
    updated_at         TIMESTAMPTZ   NOT NULL,
    created_by         UUID,
    updated_by         UUID,
    user_id            UUID          NOT NULL,
    code_hash          VARCHAR(64)   NOT NULL,
    expires_at         TIMESTAMPTZ   NOT NULL,
    attempts           INTEGER       NOT NULL DEFAULT 0,
    max_attempts       INTEGER       NOT NULL DEFAULT 5,
    verified_at        TIMESTAMPTZ,
    ticket_hash        VARCHAR(64),
    ticket_expires_at  TIMESTAMPTZ,
    consumed_at        TIMESTAMPTZ,
    CONSTRAINT pk_password_change_otps PRIMARY KEY (id),
    CONSTRAINT ck_password_change_otps_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_password_change_otps_max_attempts CHECK (max_attempts >= 1),
    CONSTRAINT fk_password_change_otps_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_password_change_otps_user   ON password_change_otps (user_id);
CREATE INDEX idx_password_change_otps_ticket ON password_change_otps (ticket_hash);
