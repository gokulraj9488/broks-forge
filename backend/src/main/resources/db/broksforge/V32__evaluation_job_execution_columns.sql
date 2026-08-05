-- ===========================================================================
-- V32: Background execution support for evaluation jobs — priority queueing,
-- resumability (retry pass counter + heartbeat) and the batch size used, so a
-- resumed job pages through the dataset the same way it originally did.
-- ===========================================================================

ALTER TABLE evaluation_jobs
    ADD COLUMN priority         INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN queued_at        TIMESTAMPTZ,
    ADD COLUMN last_progress_at TIMESTAMPTZ,
    ADD COLUMN batch_size       INTEGER,
    ADD COLUMN retry_count      INTEGER     NOT NULL DEFAULT 0;

CREATE INDEX idx_eval_jobs_status_priority ON evaluation_jobs (status, priority DESC, created_at);
