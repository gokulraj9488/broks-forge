-- ===========================================================================
-- V7: Agent versions (deployments). Closes the agents <-> versions reference.
-- ===========================================================================

CREATE TABLE agent_versions (
    id                    UUID           NOT NULL DEFAULT gen_random_uuid(),
    version               BIGINT         NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ    NOT NULL,
    updated_at            TIMESTAMPTZ    NOT NULL,
    created_by            UUID,
    updated_by            UUID,
    agent_id              UUID           NOT NULL,
    organization_id       UUID           NOT NULL,
    project_id            UUID           NOT NULL,
    version_number        VARCHAR(64)    NOT NULL,
    sequence              BIGINT         NOT NULL,
    model                 VARCHAR(128)   NOT NULL,
    provider              VARCHAR(48)    NOT NULL,
    framework_version     VARCHAR(64),
    git_commit_sha        VARCHAR(64),
    prompt_version        VARCHAR(64),
    environment           VARCHAR(32)    NOT NULL,
    release_notes         VARCHAR(2000),
    deployment_timestamp  TIMESTAMPTZ    NOT NULL,
    active                BOOLEAN        NOT NULL DEFAULT FALSE,
    rollback_ready        BOOLEAN        NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_agent_versions PRIMARY KEY (id),
    CONSTRAINT uq_agent_versions_agent_number UNIQUE (agent_id, version_number),
    CONSTRAINT ck_agent_versions_sequence CHECK (sequence >= 0),
    CONSTRAINT fk_agent_versions_agent FOREIGN KEY (agent_id) REFERENCES agents (id) ON DELETE CASCADE
);

CREATE INDEX idx_agent_versions_agent        ON agent_versions (agent_id);
CREATE INDEX idx_agent_versions_agent_active ON agent_versions (agent_id, active);

-- Now that agent_versions exists, add the agents -> active version reference.
ALTER TABLE agents
    ADD CONSTRAINT fk_agents_active_version
        FOREIGN KEY (current_active_version_id) REFERENCES agent_versions (id) ON DELETE SET NULL;
