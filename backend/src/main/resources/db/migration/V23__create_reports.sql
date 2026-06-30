-- ===========================================================================
-- V23: Reports (audit records of generated exports)
-- ===========================================================================

CREATE TABLE reports (
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
    project_id       UUID          NOT NULL,
    name             VARCHAR(200)  NOT NULL,
    type             VARCHAR(32)   NOT NULL,
    format           VARCHAR(16)   NOT NULL,
    target_id        UUID          NOT NULL,
    owner_id         UUID          NOT NULL,
    CONSTRAINT pk_reports PRIMARY KEY (id),
    CONSTRAINT fk_reports_org     FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_reports_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_reports_owner   FOREIGN KEY (owner_id) REFERENCES users (id)
);

CREATE INDEX idx_reports_project ON reports (project_id);
CREATE INDEX idx_reports_org     ON reports (organization_id);
CREATE INDEX idx_reports_target  ON reports (target_id);
