-- ===========================================================================
-- V39 — Pin the evaluation profile version a job ran against (Milestone 1).
-- ---------------------------------------------------------------------------
-- Mirrors dataset_version_id: a job records the exact profile version it used
-- so later edits to the profile can never change a historical job's metrics.
-- No FK cascade risk — EvaluationProfileVersion rows are never deleted once
-- created (same guarantee as dataset_versions/prompt_versions), so a plain FK
-- is safe. Backfill existing jobs that already reference a profile to that
-- profile's version 1 (created by V38) so existing job history gains a
-- consistent version pointer instead of being left null.
-- ===========================================================================

ALTER TABLE evaluation_jobs
    ADD COLUMN profile_version_id     UUID,
    ADD COLUMN profile_version_number INTEGER,
    ADD CONSTRAINT fk_eval_jobs_profile_version FOREIGN KEY (profile_version_id)
        REFERENCES evaluation_profile_versions (id);

UPDATE evaluation_jobs j
SET profile_version_id = p.current_version_id,
    profile_version_number = p.latest_version_number
FROM evaluation_profiles p
WHERE j.profile_id = p.id AND j.profile_id IS NOT NULL;
