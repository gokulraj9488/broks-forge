-- ===========================================================================
-- V38 — Evaluation Profile versioning (Milestone 1: complete the Evaluation
-- Profile system).
-- ---------------------------------------------------------------------------
-- Mirrors dataset_versions/prompt_versions exactly: an immutable snapshot table
-- plus a latest_version_number/current_version_id pointer pair on the
-- container. enabled defaults TRUE so every existing profile stays selectable.
-- Every existing profile is backfilled with a version 1 snapshot of its
-- current metrics/pass_threshold, so pre-existing profiles are immediately
-- usable by jobs that pin a version — nothing is left in a half-migrated state.
-- ===========================================================================

CREATE TABLE evaluation_profile_versions (
    id               UUID          NOT NULL DEFAULT gen_random_uuid(),
    version          BIGINT        NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ   NOT NULL,
    updated_at       TIMESTAMPTZ   NOT NULL,
    created_by       UUID,
    updated_by       UUID,
    profile_id       UUID          NOT NULL,
    organization_id  UUID          NOT NULL,
    project_id       UUID          NOT NULL,
    version_number   INTEGER       NOT NULL,
    metrics          TEXT,
    pass_threshold   NUMERIC(7, 4),
    CONSTRAINT pk_eval_profile_versions PRIMARY KEY (id),
    CONSTRAINT uq_eval_profile_versions_number UNIQUE (profile_id, version_number),
    CONSTRAINT fk_eval_profile_versions_profile FOREIGN KEY (profile_id) REFERENCES evaluation_profiles (id) ON DELETE CASCADE
);
CREATE INDEX idx_eval_profile_versions_profile ON evaluation_profile_versions (profile_id);
CREATE INDEX idx_eval_profile_versions_org     ON evaluation_profile_versions (organization_id);

ALTER TABLE evaluation_profiles
    ADD COLUMN enabled               BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN latest_version_number INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN current_version_id    UUID;

INSERT INTO evaluation_profile_versions (id, version, created_at, updated_at, profile_id, organization_id,
                                          project_id, version_number, metrics, pass_threshold)
SELECT gen_random_uuid(), 0, now(), now(), id, organization_id, project_id, 1, metrics, pass_threshold
FROM evaluation_profiles;

UPDATE evaluation_profiles p
SET latest_version_number = 1,
    current_version_id = v.id
FROM evaluation_profile_versions v
WHERE v.profile_id = p.id AND v.version_number = 1;
