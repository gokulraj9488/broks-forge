-- ===========================================================================
-- V34: Evaluation job events — a structured, persisted audit trail of the
-- execution engine's lifecycle transitions (queued/started/checkpoint/
-- cancelled/completed/failed/resumed), independent of application logs.
-- ===========================================================================

CREATE TABLE evaluation_job_events (
    id                UUID          NOT NULL DEFAULT gen_random_uuid(),
    evaluation_job_id UUID          NOT NULL,
    organization_id   UUID          NOT NULL,
    event_type        VARCHAR(40)   NOT NULL,
    message           VARCHAR(1000),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_eval_job_events PRIMARY KEY (id),
    CONSTRAINT fk_eval_job_events_job FOREIGN KEY (evaluation_job_id) REFERENCES evaluation_jobs (id) ON DELETE CASCADE
);

CREATE INDEX idx_eval_job_events_job ON evaluation_job_events (evaluation_job_id, created_at);
