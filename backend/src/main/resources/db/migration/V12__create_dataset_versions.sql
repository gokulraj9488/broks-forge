-- ===========================================================================
-- V12: Dataset versions (immutable snapshots). Closes datasets <-> versions.
-- ===========================================================================

CREATE TABLE dataset_versions (
    id               UUID          NOT NULL DEFAULT gen_random_uuid(),
    version          BIGINT        NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ   NOT NULL,
    updated_at       TIMESTAMPTZ   NOT NULL,
    created_by       UUID,
    updated_by       UUID,
    dataset_id       UUID          NOT NULL,
    organization_id  UUID          NOT NULL,
    project_id       UUID          NOT NULL,
    version_number   INTEGER       NOT NULL,
    description      VARCHAR(1000),
    source_format    VARCHAR(32)   NOT NULL,
    item_count       INTEGER       NOT NULL DEFAULT 0,
    columns          TEXT,
    checksum         VARCHAR(64),
    CONSTRAINT pk_dataset_versions PRIMARY KEY (id),
    CONSTRAINT uq_dataset_versions_number UNIQUE (dataset_id, version_number),
    CONSTRAINT ck_dataset_versions_number CHECK (version_number >= 1),
    CONSTRAINT ck_dataset_versions_item_count CHECK (item_count >= 0),
    CONSTRAINT fk_dataset_versions_dataset FOREIGN KEY (dataset_id) REFERENCES datasets (id) ON DELETE CASCADE
);

CREATE INDEX idx_dataset_versions_dataset ON dataset_versions (dataset_id);
CREATE INDEX idx_dataset_versions_org     ON dataset_versions (organization_id);

-- Now that dataset_versions exists, point datasets at their current version.
ALTER TABLE datasets
    ADD CONSTRAINT fk_datasets_current_version
        FOREIGN KEY (current_version_id) REFERENCES dataset_versions (id) ON DELETE SET NULL;
