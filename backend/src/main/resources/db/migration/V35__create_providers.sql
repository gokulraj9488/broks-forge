-- ===========================================================================
-- V35 — Provider abstraction milestone: first-class Provider registry.
-- ---------------------------------------------------------------------------
-- A Provider is the shared connection/authentication/capability profile that
-- agents reference instead of duplicating provider configuration. Purely
-- additive: no existing table is altered here (agents gain their provider_id
-- link in V36, after providers exist to point at).
-- ===========================================================================

CREATE TABLE providers (
    id                   UUID          NOT NULL DEFAULT gen_random_uuid(),
    version              BIGINT        NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ   NOT NULL,
    updated_at           TIMESTAMPTZ   NOT NULL,
    created_by           UUID,
    updated_by           UUID,
    deleted              BOOLEAN       NOT NULL DEFAULT FALSE,
    deleted_at           TIMESTAMPTZ,
    deleted_by           UUID,
    organization_id      UUID          NOT NULL,
    project_id           UUID          NOT NULL,
    name                 VARCHAR(120)  NOT NULL,
    type                 VARCHAR(48)   NOT NULL,
    base_url             VARCHAR(2048) NOT NULL,
    auth_type            VARCHAR(32)   NOT NULL DEFAULT 'NONE',
    encrypted_api_key    TEXT,
    api_key_hint         VARCHAR(16),
    key_version          INTEGER,
    default_headers      TEXT,
    default_model        VARCHAR(128),
    supported_models     TEXT,
    -- Embedded capabilities (same shape as agents.cap_*, see V6/AgentCapabilities)
    cap_streaming         BOOLEAN      NOT NULL DEFAULT FALSE,
    cap_memory            BOOLEAN      NOT NULL DEFAULT FALSE,
    cap_rag               BOOLEAN      NOT NULL DEFAULT FALSE,
    cap_tool_calling      BOOLEAN      NOT NULL DEFAULT FALSE,
    cap_structured_output BOOLEAN      NOT NULL DEFAULT FALSE,
    cap_reasoning         BOOLEAN      NOT NULL DEFAULT FALSE,
    cap_multi_agent       BOOLEAN      NOT NULL DEFAULT FALSE,
    custom_metadata       TEXT,
    rate_limits          TEXT,
    pricing_metadata     TEXT,
    health_status        VARCHAR(32)   NOT NULL DEFAULT 'UNKNOWN',
    last_health_check_at TIMESTAMPTZ,
    CONSTRAINT pk_providers PRIMARY KEY (id),
    CONSTRAINT fk_providers_org     FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_providers_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
);

CREATE INDEX idx_providers_project ON providers (project_id);
CREATE INDEX idx_providers_org     ON providers (organization_id);
CREATE INDEX idx_providers_type    ON providers (type);
