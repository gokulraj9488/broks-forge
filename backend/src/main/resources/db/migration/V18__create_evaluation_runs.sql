-- ===========================================================================
-- V18: Evaluation runs (one execution per dataset item within a job)
-- ===========================================================================

CREATE TABLE evaluation_runs (
    id                  UUID          NOT NULL DEFAULT gen_random_uuid(),
    version             BIGINT        NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL,
    updated_at          TIMESTAMPTZ   NOT NULL,
    created_by          UUID,
    updated_by          UUID,
    evaluation_job_id   UUID          NOT NULL,
    organization_id     UUID          NOT NULL,
    dataset_item_id     UUID,
    sequence            INTEGER       NOT NULL,
    status              VARCHAR(32)   NOT NULL DEFAULT 'PENDING',
    input               TEXT,
    output              TEXT,
    latency_ms          BIGINT,
    prompt_tokens       INTEGER,
    completion_tokens   INTEGER,
    total_tokens        INTEGER,
    cost                NUMERIC(18, 6),
    http_status         INTEGER,
    passed              BOOLEAN,
    score               NUMERIC(7, 4),
    error               VARCHAR(1000),
    started_at          TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    CONSTRAINT pk_eval_runs PRIMARY KEY (id),
    CONSTRAINT ck_eval_runs_sequence CHECK (sequence >= 0),
    CONSTRAINT ck_eval_runs_latency CHECK (latency_ms IS NULL OR latency_ms >= 0),
    CONSTRAINT fk_eval_runs_job FOREIGN KEY (evaluation_job_id) REFERENCES evaluation_jobs (id) ON DELETE CASCADE
);

CREATE INDEX idx_eval_runs_job        ON evaluation_runs (evaluation_job_id);
CREATE INDEX idx_eval_runs_job_status ON evaluation_runs (evaluation_job_id, status);
CREATE INDEX idx_eval_runs_org        ON evaluation_runs (organization_id);
