-- ===========================================================================
-- V22: Regression checks (candidate-vs-baseline evaluation comparisons)
-- ===========================================================================

CREATE TABLE regression_checks (
    id                 UUID          NOT NULL DEFAULT gen_random_uuid(),
    version            BIGINT        NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ   NOT NULL,
    updated_at         TIMESTAMPTZ   NOT NULL,
    created_by         UUID,
    updated_by         UUID,
    deleted            BOOLEAN       NOT NULL DEFAULT FALSE,
    deleted_at         TIMESTAMPTZ,
    deleted_by         UUID,
    organization_id    UUID          NOT NULL,
    project_id         UUID          NOT NULL,
    name               VARCHAR(160)  NOT NULL,
    baseline_job_id    UUID          NOT NULL,
    candidate_job_id   UUID          NOT NULL,
    tolerance_pct      NUMERIC(6, 3) NOT NULL DEFAULT 10,
    regressed          BOOLEAN       NOT NULL DEFAULT FALSE,
    findings           TEXT,
    CONSTRAINT pk_regression_checks PRIMARY KEY (id),
    CONSTRAINT ck_regression_tolerance CHECK (tolerance_pct >= 0),
    CONSTRAINT fk_regression_org      FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_regression_project  FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_regression_baseline FOREIGN KEY (baseline_job_id) REFERENCES evaluation_jobs (id) ON DELETE CASCADE,
    CONSTRAINT fk_regression_candidate FOREIGN KEY (candidate_job_id) REFERENCES evaluation_jobs (id) ON DELETE CASCADE
);

CREATE INDEX idx_regression_checks_project   ON regression_checks (project_id);
CREATE INDEX idx_regression_checks_org       ON regression_checks (organization_id);
CREATE INDEX idx_regression_checks_regressed ON regression_checks (regressed);
