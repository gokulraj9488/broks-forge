-- ===========================================================================
-- V11: Datasets (named, project-scoped evaluation data containers)
-- ===========================================================================

CREATE TABLE datasets (
    id                     UUID          NOT NULL DEFAULT gen_random_uuid(),
    version                BIGINT        NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ   NOT NULL,
    updated_at             TIMESTAMPTZ   NOT NULL,
    created_by             UUID,
    updated_by             UUID,
    deleted                BOOLEAN       NOT NULL DEFAULT FALSE,
    deleted_at             TIMESTAMPTZ,
    deleted_by             UUID,
    organization_id        UUID          NOT NULL,
    project_id             UUID          NOT NULL,
    name                   VARCHAR(120)  NOT NULL,
    slug                   VARCHAR(64)   NOT NULL,
    description            VARCHAR(1000),
    owner_id               UUID          NOT NULL,
    visibility             VARCHAR(32)   NOT NULL DEFAULT 'PRIVATE',
    status                 VARCHAR(32)   NOT NULL DEFAULT 'ACTIVE',
    tags                   TEXT,
    latest_version_number  INTEGER       NOT NULL DEFAULT 0,
    current_version_id     UUID,
    current_item_count     INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT pk_datasets PRIMARY KEY (id),
    CONSTRAINT uq_datasets_project_slug UNIQUE (project_id, slug),
    CONSTRAINT ck_datasets_latest_version CHECK (latest_version_number >= 0),
    CONSTRAINT ck_datasets_item_count CHECK (current_item_count >= 0),
    CONSTRAINT fk_datasets_org     FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_datasets_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_datasets_owner   FOREIGN KEY (owner_id) REFERENCES users (id)
);

CREATE INDEX idx_datasets_project ON datasets (project_id);
CREATE INDEX idx_datasets_org     ON datasets (organization_id);
CREATE INDEX idx_datasets_owner   ON datasets (owner_id);
CREATE INDEX idx_datasets_status  ON datasets (status);
