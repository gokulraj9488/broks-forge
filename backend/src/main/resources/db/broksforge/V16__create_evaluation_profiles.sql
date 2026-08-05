-- ===========================================================================
-- V16: Evaluation profiles (reusable metric + threshold rubrics)
-- ===========================================================================

CREATE TABLE evaluation_profiles (
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
    name             VARCHAR(120)  NOT NULL,
    slug             VARCHAR(64)   NOT NULL,
    description      VARCHAR(1000),
    owner_id         UUID          NOT NULL,
    metrics          TEXT,
    pass_threshold   NUMERIC(7, 4),
    CONSTRAINT pk_eval_profiles PRIMARY KEY (id),
    CONSTRAINT uq_eval_profiles_project_slug UNIQUE (project_id, slug),
    CONSTRAINT ck_eval_profiles_threshold CHECK (pass_threshold IS NULL OR (pass_threshold >= 0 AND pass_threshold <= 1)),
    CONSTRAINT fk_eval_profiles_org     FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_eval_profiles_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_eval_profiles_owner   FOREIGN KEY (owner_id) REFERENCES users (id)
);

CREATE INDEX idx_eval_profiles_project ON evaluation_profiles (project_id);
CREATE INDEX idx_eval_profiles_org     ON evaluation_profiles (organization_id);
