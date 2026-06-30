-- ===========================================================================
-- V21: Benchmark entries (one evaluation job per competitor)
-- ===========================================================================

CREATE TABLE benchmark_entries (
    id                  UUID          NOT NULL DEFAULT gen_random_uuid(),
    version             BIGINT        NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL,
    updated_at          TIMESTAMPTZ   NOT NULL,
    created_by          UUID,
    updated_by          UUID,
    benchmark_id        UUID          NOT NULL,
    organization_id     UUID          NOT NULL,
    evaluation_job_id   UUID          NOT NULL,
    label               VARCHAR(160)  NOT NULL,
    CONSTRAINT pk_benchmark_entries PRIMARY KEY (id),
    CONSTRAINT uq_benchmark_entries_job UNIQUE (benchmark_id, evaluation_job_id),
    CONSTRAINT fk_benchmark_entries_benchmark FOREIGN KEY (benchmark_id) REFERENCES benchmarks (id) ON DELETE CASCADE,
    CONSTRAINT fk_benchmark_entries_job FOREIGN KEY (evaluation_job_id) REFERENCES evaluation_jobs (id) ON DELETE CASCADE
);

CREATE INDEX idx_benchmark_entries_benchmark ON benchmark_entries (benchmark_id);
