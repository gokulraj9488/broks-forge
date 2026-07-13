-- ===========================================================================
-- V33: Evaluation run attempt/pass number — lets a resumed or retried job
-- re-execute an item without losing the history of earlier attempts. Resume
-- logic reads the latest attempt per (job, dataset_item) to decide what's
-- still outstanding.
-- ===========================================================================

ALTER TABLE evaluation_runs
    ADD COLUMN attempt INTEGER NOT NULL DEFAULT 1;

CREATE INDEX idx_eval_runs_job_item ON evaluation_runs (evaluation_job_id, dataset_item_id, attempt DESC);
