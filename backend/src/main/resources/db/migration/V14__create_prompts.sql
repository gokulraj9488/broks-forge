-- ===========================================================================
-- V14: Prompts (named, reusable prompt-library entries)
-- ===========================================================================

CREATE TABLE prompts (
    id                          UUID          NOT NULL DEFAULT gen_random_uuid(),
    version                     BIGINT        NOT NULL DEFAULT 0,
    created_at                  TIMESTAMPTZ   NOT NULL,
    updated_at                  TIMESTAMPTZ   NOT NULL,
    created_by                  UUID,
    updated_by                  UUID,
    deleted                     BOOLEAN       NOT NULL DEFAULT FALSE,
    deleted_at                  TIMESTAMPTZ,
    deleted_by                  UUID,
    organization_id             UUID          NOT NULL,
    project_id                  UUID          NOT NULL,
    name                        VARCHAR(120)  NOT NULL,
    slug                        VARCHAR(64)   NOT NULL,
    description                 VARCHAR(1000),
    owner_id                    UUID          NOT NULL,
    status                      VARCHAR(32)   NOT NULL DEFAULT 'ACTIVE',
    tags                        TEXT,
    latest_version_number       INTEGER       NOT NULL DEFAULT 0,
    current_active_version_id   UUID,
    CONSTRAINT pk_prompts PRIMARY KEY (id),
    CONSTRAINT uq_prompts_project_slug UNIQUE (project_id, slug),
    CONSTRAINT ck_prompts_latest_version CHECK (latest_version_number >= 0),
    CONSTRAINT fk_prompts_org     FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_prompts_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_prompts_owner   FOREIGN KEY (owner_id) REFERENCES users (id)
);

CREATE INDEX idx_prompts_project ON prompts (project_id);
CREATE INDEX idx_prompts_org     ON prompts (organization_id);
CREATE INDEX idx_prompts_owner   ON prompts (owner_id);
CREATE INDEX idx_prompts_status  ON prompts (status);
