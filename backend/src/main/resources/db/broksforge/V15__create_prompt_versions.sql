-- ===========================================================================
-- V15: Prompt versions (immutable template snapshots). Closes prompts <-> versions.
-- ===========================================================================

CREATE TABLE prompt_versions (
    id               UUID          NOT NULL DEFAULT gen_random_uuid(),
    version          BIGINT        NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ   NOT NULL,
    updated_at       TIMESTAMPTZ   NOT NULL,
    created_by       UUID,
    updated_by       UUID,
    prompt_id        UUID          NOT NULL,
    organization_id  UUID          NOT NULL,
    project_id       UUID          NOT NULL,
    version_number   INTEGER       NOT NULL,
    template         TEXT          NOT NULL,
    variables        TEXT,
    notes            VARCHAR(1000),
    provider         VARCHAR(48),
    model            VARCHAR(128),
    active           BOOLEAN       NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_prompt_versions PRIMARY KEY (id),
    CONSTRAINT uq_prompt_versions_number UNIQUE (prompt_id, version_number),
    CONSTRAINT ck_prompt_versions_number CHECK (version_number >= 1),
    CONSTRAINT fk_prompt_versions_prompt FOREIGN KEY (prompt_id) REFERENCES prompts (id) ON DELETE CASCADE
);

CREATE INDEX idx_prompt_versions_prompt        ON prompt_versions (prompt_id);
CREATE INDEX idx_prompt_versions_prompt_active ON prompt_versions (prompt_id, active);

-- Now that prompt_versions exists, point prompts at their active version.
ALTER TABLE prompts
    ADD CONSTRAINT fk_prompts_active_version
        FOREIGN KEY (current_active_version_id) REFERENCES prompt_versions (id) ON DELETE SET NULL;
