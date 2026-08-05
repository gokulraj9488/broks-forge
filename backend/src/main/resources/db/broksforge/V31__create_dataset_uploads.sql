-- Upload history/audit trail for the dataset file-upload path (CSV/JSON/XLSX/ZIP).
-- Purely additive: the existing paste-mode import (POST .../datasets/{id}/versions with an
-- inline ImportDatasetRequest body) is untouched and does not write to this table.
-- Not soft-deletable: like agent_health_checks, this is an append-only audit trail, never
-- deleted via the API, so it carries the universal BaseEntity skeleton only (no deleted_*).
CREATE TABLE dataset_uploads (
    id                   UUID          NOT NULL DEFAULT gen_random_uuid(),
    version              BIGINT        NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ   NOT NULL,
    updated_at           TIMESTAMPTZ   NOT NULL,
    created_by           UUID,
    updated_by           UUID,
    organization_id      UUID          NOT NULL,
    project_id           UUID          NOT NULL,
    dataset_id           UUID          NOT NULL,
    filename             VARCHAR(255)  NOT NULL,
    content_type         VARCHAR(100),
    format               VARCHAR(32)   NOT NULL,
    size_bytes           BIGINT        NOT NULL,
    checksum             VARCHAR(64)   NOT NULL,
    status               VARCHAR(32)   NOT NULL DEFAULT 'PENDING',
    row_count            INTEGER,
    column_count         INTEGER,
    dataset_version_id   UUID,
    error_message        TEXT,
    CONSTRAINT pk_dataset_uploads PRIMARY KEY (id),
    CONSTRAINT fk_dataset_uploads_dataset FOREIGN KEY (dataset_id) REFERENCES datasets (id) ON DELETE CASCADE,
    CONSTRAINT fk_dataset_uploads_version FOREIGN KEY (dataset_version_id) REFERENCES dataset_versions (id)
);

CREATE INDEX idx_dataset_uploads_dataset ON dataset_uploads (dataset_id);
-- Speeds up duplicate-content detection (same dataset, same checksum, already completed).
CREATE INDEX idx_dataset_uploads_dataset_checksum ON dataset_uploads (dataset_id, checksum);
