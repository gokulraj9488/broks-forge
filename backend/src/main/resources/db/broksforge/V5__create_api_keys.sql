-- ===========================================================================
-- V5: API keys (project-scoped). Only the SHA-256 secret hash is stored.
-- ===========================================================================

CREATE TABLE api_keys (
    id               UUID          NOT NULL DEFAULT gen_random_uuid(),
    version          BIGINT        NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ   NOT NULL,
    updated_at       TIMESTAMPTZ   NOT NULL,
    created_by       UUID,
    updated_by       UUID,
    organization_id  UUID          NOT NULL,
    project_id       UUID          NOT NULL,
    name             VARCHAR(120)  NOT NULL,
    key_prefix       VARCHAR(32)   NOT NULL,
    secret_hash      VARCHAR(64)   NOT NULL,
    last_used_at     TIMESTAMPTZ,
    expires_at       TIMESTAMPTZ,
    revoked          BOOLEAN       NOT NULL DEFAULT FALSE,
    revoked_at       TIMESTAMPTZ,
    CONSTRAINT pk_api_keys PRIMARY KEY (id),
    CONSTRAINT uq_api_keys_prefix UNIQUE (key_prefix),
    CONSTRAINT fk_api_keys_org     FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_api_keys_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
);

CREATE INDEX idx_api_keys_project ON api_keys (project_id);
CREATE INDEX idx_api_keys_org     ON api_keys (organization_id);
CREATE INDEX idx_api_keys_prefix  ON api_keys (key_prefix);
