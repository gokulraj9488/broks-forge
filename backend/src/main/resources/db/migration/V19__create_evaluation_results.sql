-- ===========================================================================
-- V19: Evaluation results (one metric score per run)
-- ===========================================================================

CREATE TABLE evaluation_results (
    id                  UUID          NOT NULL DEFAULT gen_random_uuid(),
    version             BIGINT        NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL,
    updated_at          TIMESTAMPTZ   NOT NULL,
    created_by          UUID,
    updated_by          UUID,
    evaluation_run_id   UUID          NOT NULL,
    evaluation_job_id   UUID          NOT NULL,
    organization_id     UUID          NOT NULL,
    metric_type         VARCHAR(48)   NOT NULL,
    metric_label        VARCHAR(120),
    passed              BOOLEAN       NOT NULL,
    score               NUMERIC(7, 4) NOT NULL,
    threshold           NUMERIC(18, 6),
    detail              VARCHAR(1000),
    CONSTRAINT pk_eval_results PRIMARY KEY (id),
    CONSTRAINT fk_eval_results_run FOREIGN KEY (evaluation_run_id) REFERENCES evaluation_runs (id) ON DELETE CASCADE,
    CONSTRAINT fk_eval_results_job FOREIGN KEY (evaluation_job_id) REFERENCES evaluation_jobs (id) ON DELETE CASCADE
);

CREATE INDEX idx_eval_results_run        ON evaluation_results (evaluation_run_id);
CREATE INDEX idx_eval_results_job        ON evaluation_results (evaluation_job_id);
CREATE INDEX idx_eval_results_job_metric ON evaluation_results (evaluation_job_id, metric_type);
