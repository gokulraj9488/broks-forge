-- ===========================================================================
-- V1: Users and system roles
-- ===========================================================================

CREATE TABLE users (
    id                 UUID            NOT NULL DEFAULT gen_random_uuid(),
    version            BIGINT          NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ     NOT NULL,
    updated_at         TIMESTAMPTZ     NOT NULL,
    created_by         UUID,
    updated_by         UUID,
    deleted            BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at         TIMESTAMPTZ,
    deleted_by         UUID,
    email              VARCHAR(254)    NOT NULL,
    password_hash      VARCHAR(100)    NOT NULL,
    first_name         VARCHAR(100),
    last_name          VARCHAR(100),
    status             VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
    email_verified     BOOLEAN         NOT NULL DEFAULT FALSE,
    email_verified_at  TIMESTAMPTZ,
    last_login_at      TIMESTAMPTZ,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE INDEX idx_users_email  ON users (email);
CREATE INDEX idx_users_status ON users (status);

CREATE TABLE user_roles (
    user_id  UUID         NOT NULL,
    role     VARCHAR(32)  NOT NULL,
    CONSTRAINT uq_user_roles UNIQUE (user_id, role),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_user_roles_user ON user_roles (user_id);
