-- ===========================================================================
-- V8: Agent credentials. Secrets are AES-256-GCM ciphertext, never plaintext.
-- ===========================================================================

CREATE TABLE agent_credentials (
    id                UUID          NOT NULL DEFAULT gen_random_uuid(),
    version           BIGINT        NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    created_by        UUID,
    updated_by        UUID,
    agent_id          UUID          NOT NULL,
    organization_id   UUID          NOT NULL,
    project_id        UUID          NOT NULL,
    auth_type         VARCHAR(32)   NOT NULL,
    username          VARCHAR(256),
    header_name       VARCHAR(128),
    encrypted_secret  TEXT,
    secret_hint       VARCHAR(16),
    key_version       INTEGER       NOT NULL DEFAULT 1,
    active            BOOLEAN       NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_agent_credentials PRIMARY KEY (id),
    CONSTRAINT ck_agent_credentials_key_version CHECK (key_version >= 1),
    CONSTRAINT fk_agent_credentials_agent FOREIGN KEY (agent_id) REFERENCES agents (id) ON DELETE CASCADE
);

CREATE INDEX idx_agent_credentials_agent        ON agent_credentials (agent_id);
CREATE INDEX idx_agent_credentials_agent_active ON agent_credentials (agent_id, active);
