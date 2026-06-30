-- ===========================================================================
-- V20: Benchmarks (named comparisons of evaluation jobs)
-- ===========================================================================

CREATE TABLE benchmarks (
    id               UUID          NOT NULL DEFAULT gen_random_uuid(),
    version          BIGINT        NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ   NOT NULL,
    updated_at       TIMESTAMPTZ   NOT NULL,
    created_by       UUID,
    updated_by       UUID,
    deleted          BOOLEAN       NOT NULL DEFAULT FALSE,
    deleted_at       TIMESTAMPTZ,
    deleted_by       UUID,
    organization_id  UUID          NOT NULL,
    project_id       UUID          NOT NULL,
    name             VARCHAR(160)  NOT NULL,
    description      VARCHAR(1000),
    owner_id         UUID          NOT NULL,
    type             VARCHAR(48)   NOT NULL,
    metric_key       VARCHAR(64)   NOT NULL DEFAULT 'passRate',
    CONSTRAINT pk_benchmarks PRIMARY KEY (id),
    CONSTRAINT fk_benchmarks_org     FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_benchmarks_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_benchmarks_owner   FOREIGN KEY (owner_id) REFERENCES users (id)
);

CREATE INDEX idx_benchmarks_project ON benchmarks (project_id);
CREATE INDEX idx_benchmarks_org     ON benchmarks (organization_id);
