-- ===========================================================================
-- V17: Evaluation jobs (the top-level evaluation aggregate)
-- ===========================================================================

CREATE TABLE evaluation_jobs (
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
    name                 VARCHAR(160)  NOT NULL,
    owner_id             UUID          NOT NULL,
    status               VARCHAR(32)   NOT NULL DEFAULT 'PENDING',
    target_type          VARCHAR(32)   NOT NULL DEFAULT 'AGENT',
    agent_id             UUID          NOT NULL,
    agent_version_id     UUID,
    dataset_id           UUID          NOT NULL,
    dataset_version_id   UUID          NOT NULL,
    prompt_id            UUID,
    prompt_version_id    UUID,
    profile_id           UUID,
    provider             VARCHAR(48),
    model                VARCHAR(128),
    parameters           TEXT,
    total_items          INTEGER       NOT NULL DEFAULT 0,
    completed_items      INTEGER       NOT NULL DEFAULT 0,
    failed_items         INTEGER       NOT NULL DEFAULT 0,
    started_at           TIMESTAMPTZ,
    completed_at         TIMESTAMPTZ,
    summary              TEXT,
    error_message        VARCHAR(1000),
    CONSTRAINT pk_eval_jobs PRIMARY KEY (id),
    CONSTRAINT ck_eval_jobs_total      CHECK (total_items >= 0),
    CONSTRAINT ck_eval_jobs_completed  CHECK (completed_items >= 0),
    CONSTRAINT ck_eval_jobs_failed     CHECK (failed_items >= 0),
    CONSTRAINT fk_eval_jobs_org        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_eval_jobs_project    FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_eval_jobs_agent      FOREIGN KEY (agent_id) REFERENCES agents (id) ON DELETE CASCADE,
    CONSTRAINT fk_eval_jobs_dataset    FOREIGN KEY (dataset_id) REFERENCES datasets (id) ON DELETE CASCADE,
    CONSTRAINT fk_eval_jobs_dataset_ver FOREIGN KEY (dataset_version_id) REFERENCES dataset_versions (id) ON DELETE CASCADE,
    CONSTRAINT fk_eval_jobs_profile    FOREIGN KEY (profile_id) REFERENCES evaluation_profiles (id) ON DELETE SET NULL
);

CREATE INDEX idx_eval_jobs_project ON evaluation_jobs (project_id);
CREATE INDEX idx_eval_jobs_org     ON evaluation_jobs (organization_id);
CREATE INDEX idx_eval_jobs_agent   ON evaluation_jobs (agent_id);
CREATE INDEX idx_eval_jobs_dataset ON evaluation_jobs (dataset_id);
CREATE INDEX idx_eval_jobs_status  ON evaluation_jobs (status);
CREATE INDEX idx_eval_jobs_created ON evaluation_jobs (created_at);
