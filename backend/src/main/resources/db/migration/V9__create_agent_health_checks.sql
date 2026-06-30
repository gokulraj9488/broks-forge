-- ===========================================================================
-- V9: Agent health checks (observation history).
-- ===========================================================================

CREATE TABLE agent_health_checks (
    id               UUID           NOT NULL DEFAULT gen_random_uuid(),
    version          BIGINT         NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ    NOT NULL,
    updated_at       TIMESTAMPTZ    NOT NULL,
    created_by       UUID,
    updated_by       UUID,
    agent_id         UUID           NOT NULL,
    organization_id  UUID           NOT NULL,
    project_id       UUID           NOT NULL,
    version_id       UUID,
    check_type       VARCHAR(32)    NOT NULL,
    status           VARCHAR(32)    NOT NULL,
    success          BOOLEAN        NOT NULL,
    http_status      INTEGER,
    latency_ms       BIGINT,
    checked_at       TIMESTAMPTZ    NOT NULL,
    failure_reason   VARCHAR(1000),
    CONSTRAINT pk_agent_health_checks PRIMARY KEY (id),
    CONSTRAINT ck_agent_health_checks_http CHECK (http_status IS NULL OR (http_status BETWEEN 100 AND 599)),
    CONSTRAINT ck_agent_health_checks_latency CHECK (latency_ms IS NULL OR latency_ms >= 0),
    CONSTRAINT fk_agent_health_checks_agent FOREIGN KEY (agent_id) REFERENCES agents (id) ON DELETE CASCADE,
    CONSTRAINT fk_agent_health_checks_version FOREIGN KEY (version_id) REFERENCES agent_versions (id) ON DELETE SET NULL
);

CREATE INDEX idx_agent_health_checks_agent      ON agent_health_checks (agent_id);
CREATE INDEX idx_agent_health_checks_agent_time ON agent_health_checks (agent_id, checked_at);
