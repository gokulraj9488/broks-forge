-- ===========================================================================
-- V10: Agent tags (labels for organisation and filtering).
-- ===========================================================================

CREATE TABLE agent_tags (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    created_by       UUID,
    updated_by       UUID,
    agent_id         UUID         NOT NULL,
    organization_id  UUID         NOT NULL,
    label            VARCHAR(64)  NOT NULL,
    CONSTRAINT pk_agent_tags PRIMARY KEY (id),
    CONSTRAINT uq_agent_tags_agent_label UNIQUE (agent_id, label),
    CONSTRAINT fk_agent_tags_agent FOREIGN KEY (agent_id) REFERENCES agents (id) ON DELETE CASCADE,
    CONSTRAINT fk_agent_tags_org   FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE
);

CREATE INDEX idx_agent_tags_agent ON agent_tags (agent_id);
CREATE INDEX idx_agent_tags_label ON agent_tags (label);
