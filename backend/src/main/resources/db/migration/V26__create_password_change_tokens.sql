-- ===========================================================================
-- V26: Password change verification tokens.
-- A password change now requires clicking an emailed one-time link after the
-- current password has been verified. Same shape and rules as the other
-- lifecycle tokens (V2): only SHA-256 hashes are stored, single-use, expiring.
-- ===========================================================================

CREATE TABLE password_change_tokens (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    created_by  UUID,
    updated_by  UUID,
    user_id     UUID         NOT NULL,
    token_hash  VARCHAR(64)  NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    used_at     TIMESTAMPTZ,
    CONSTRAINT pk_password_change_tokens PRIMARY KEY (id),
    CONSTRAINT uq_password_change_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_change_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_password_change_tokens_user ON password_change_tokens (user_id);
