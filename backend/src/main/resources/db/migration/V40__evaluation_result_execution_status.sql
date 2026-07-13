-- Distinguishes a metric that never ran (provider auth failure, timeout, rate limit, ...) from one
-- that ran and produced a real score. Every existing row completed under the old all-or-nothing
-- model, so it backfills as COMPLETED with its existing passed/score untouched.
ALTER TABLE evaluation_results
    ADD COLUMN execution_status VARCHAR(40) NOT NULL DEFAULT 'COMPLETED';

ALTER TABLE evaluation_results
    ALTER COLUMN passed DROP NOT NULL;

ALTER TABLE evaluation_results
    ALTER COLUMN score DROP NOT NULL;
