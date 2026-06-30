-- ===========================================================================
-- V4: Projects
-- ===========================================================================

CREATE TABLE projects (
    id               UUID          NOT NULL DEFAULT gen_random_uuid(),
    version          BIGINT        NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ   NOT NULL,
    updated_at       TIMESTAMPTZ   NOT NULL,
    created_by       UUID,
    updated_by       UUID,
    deleted          BOOLEAN       NOT NULL DEFAULT FALSE,
    deleted_at       TIMESTAMPTZ,
    deleted_by       UUID,
    organization_id  UUID          NOT NULL,
    name             VARCHAR(120)  NOT NULL,
    slug             VARCHAR(64)   NOT NULL,
    description      VARCHAR(1000),
    status           VARCHAR(32)   NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT pk_projects PRIMARY KEY (id),
    CONSTRAINT uq_projects_org_slug UNIQUE (organization_id, slug),
    CONSTRAINT fk_projects_org FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE
);

CREATE INDEX idx_projects_org    ON projects (organization_id);
CREATE INDEX idx_projects_status ON projects (status);
