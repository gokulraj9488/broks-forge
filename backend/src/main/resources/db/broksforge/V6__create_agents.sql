-- ===========================================================================
-- V6: Agents (the central platform entity)
-- ===========================================================================

CREATE TABLE agents (
    id                         UUID           NOT NULL DEFAULT gen_random_uuid(),
    version                    BIGINT         NOT NULL DEFAULT 0,
    created_at                 TIMESTAMPTZ    NOT NULL,
    updated_at                 TIMESTAMPTZ    NOT NULL,
    created_by                 UUID,
    updated_by                 UUID,
    deleted                    BOOLEAN        NOT NULL DEFAULT FALSE,
    deleted_at                 TIMESTAMPTZ,
    deleted_by                 UUID,
    organization_id            UUID           NOT NULL,
    project_id                 UUID           NOT NULL,
    name                       VARCHAR(120)   NOT NULL,
    slug                       VARCHAR(64)    NOT NULL,
    description                VARCHAR(1000),
    owner_id                   UUID           NOT NULL,
    visibility                 VARCHAR(32)    NOT NULL DEFAULT 'PRIVATE',
    framework                  VARCHAR(48)    NOT NULL,
    language                   VARCHAR(32)    NOT NULL,
    endpoint_url               VARCHAR(2048)  NOT NULL,
    auth_type                  VARCHAR(32)    NOT NULL DEFAULT 'NONE',
    current_active_version_id  UUID,
    health_status              VARCHAR(32)    NOT NULL DEFAULT 'UNKNOWN',
    last_health_check_at       TIMESTAMPTZ,
    status                     VARCHAR(32)    NOT NULL DEFAULT 'ACTIVE',
    -- Embedded capabilities
    cap_streaming              BOOLEAN        NOT NULL DEFAULT FALSE,
    cap_memory                 BOOLEAN        NOT NULL DEFAULT FALSE,
    cap_rag                    BOOLEAN        NOT NULL DEFAULT FALSE,
    cap_tool_calling           BOOLEAN        NOT NULL DEFAULT FALSE,
    cap_structured_output      BOOLEAN        NOT NULL DEFAULT FALSE,
    cap_reasoning              BOOLEAN        NOT NULL DEFAULT FALSE,
    cap_multi_agent            BOOLEAN        NOT NULL DEFAULT FALSE,
    custom_metadata            TEXT,
    CONSTRAINT pk_agents PRIMARY KEY (id),
    CONSTRAINT uq_agents_project_slug UNIQUE (project_id, slug),
    CONSTRAINT fk_agents_org     FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_agents_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_agents_owner   FOREIGN KEY (owner_id) REFERENCES users (id)
);

CREATE INDEX idx_agents_project       ON agents (project_id);
CREATE INDEX idx_agents_org           ON agents (organization_id);
CREATE INDEX idx_agents_owner         ON agents (owner_id);
CREATE INDEX idx_agents_framework     ON agents (framework);
CREATE INDEX idx_agents_status        ON agents (status);
CREATE INDEX idx_agents_health_status ON agents (health_status);
