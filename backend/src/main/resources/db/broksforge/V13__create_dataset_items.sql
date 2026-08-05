-- ===========================================================================
-- V13: Dataset items (the rows of an immutable dataset version)
-- ===========================================================================

CREATE TABLE dataset_items (
    id                   UUID          NOT NULL DEFAULT gen_random_uuid(),
    version              BIGINT        NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ   NOT NULL,
    updated_at           TIMESTAMPTZ   NOT NULL,
    created_by           UUID,
    updated_by           UUID,
    dataset_version_id   UUID          NOT NULL,
    dataset_id           UUID          NOT NULL,
    organization_id      UUID          NOT NULL,
    sequence             INTEGER       NOT NULL,
    input                TEXT          NOT NULL,
    expected_output      TEXT,
    metadata             TEXT,
    CONSTRAINT pk_dataset_items PRIMARY KEY (id),
    CONSTRAINT uq_dataset_items_sequence UNIQUE (dataset_version_id, sequence),
    CONSTRAINT ck_dataset_items_sequence CHECK (sequence >= 0),
    CONSTRAINT fk_dataset_items_version FOREIGN KEY (dataset_version_id) REFERENCES dataset_versions (id) ON DELETE CASCADE,
    CONSTRAINT fk_dataset_items_dataset FOREIGN KEY (dataset_id) REFERENCES datasets (id) ON DELETE CASCADE
);

CREATE INDEX idx_dataset_items_version ON dataset_items (dataset_version_id);
CREATE INDEX idx_dataset_items_dataset ON dataset_items (dataset_id);
