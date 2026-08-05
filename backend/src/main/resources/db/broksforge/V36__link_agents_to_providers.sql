-- ===========================================================================
-- V36 — Provider abstraction milestone: link agents to providers.
-- ---------------------------------------------------------------------------
-- Purely additive: three nullable columns, no existing column is altered,
-- dropped or made non-null. An agent with provider_id = NULL behaves exactly
-- as it did before this migration — endpoint_url/auth_type/agent_versions.model
-- remain fully authoritative and untouched, so this cannot regress any
-- existing evaluation, benchmark, analytics or insights data (none of which
-- reference this column).
-- ===========================================================================

ALTER TABLE agents
    ADD COLUMN provider_id       UUID,
    ADD COLUMN model_override    VARCHAR(128),
    ADD COLUMN endpoint_override VARCHAR(2048),
    ADD CONSTRAINT fk_agents_provider FOREIGN KEY (provider_id) REFERENCES providers (id) ON DELETE SET NULL;

CREATE INDEX idx_agents_provider ON agents (provider_id);

-- ---------------------------------------------------------------------------
-- Automatic backfill: create one Provider per distinct (project, detected
-- provider type, exact endpoint URL) combination among existing non-deleted
-- agents, and link every matching agent to it via provider_id. providers.base_url
-- is set to the agent's full, already-working endpoint_url (path included) —
-- not just the host — because that is exactly what a new agent linking to this
-- provider with no endpointOverride inherits verbatim as its own effective
-- endpoint_url (see AgentService.applyProviderLink); truncating to the host
-- would silently produce a broken endpoint (e.g. missing
-- "/openai/v1/chat/completions") for every agent inherited from it.
--
-- Agent ids, endpoint_url, auth_type and all agent_versions/evaluation/
-- benchmark/analytics/insights data are untouched — this only populates the
-- new, previously-nonexistent provider_id column. Existing per-agent
-- credentials (agent_credentials) are NOT migrated into
-- providers.encrypted_api_key: mapping every AgentAuthType variant
-- (BASIC_AUTH's username, CUSTOM_HEADER's arbitrary header name) onto a
-- single provider-level key is not a safe 1:1 migration to do automatically,
-- so per-agent authentication continues exactly as before via
-- AgentCredentialService (see the migration report).
--
-- Provider type is detected the same way HealthProbePlanner.detectProvider
-- does it in Java (host substring match), reimplemented here in SQL so the
-- backfill is a single transactional migration rather than a separate
-- application bootstrap step.
-- ---------------------------------------------------------------------------

WITH classified AS (
    SELECT
        a.id AS agent_id,
        a.project_id,
        a.organization_id,
        a.endpoint_url,
        regexp_replace(a.endpoint_url, '^[a-zA-Z][a-zA-Z0-9+.-]*://([^/]+).*$', '\1') AS host,
        CASE
            WHEN a.endpoint_url ~* 'groq\.com'                       THEN 'GROQ'
            WHEN a.endpoint_url ~* 'openai\.azure\.com'               THEN 'AZURE_OPENAI'
            WHEN a.endpoint_url ~* 'api\.openai\.com'                 THEN 'OPENAI'
            WHEN a.endpoint_url ~* 'anthropic\.com'                   THEN 'ANTHROPIC'
            WHEN a.endpoint_url ~* 'openrouter\.ai'                   THEN 'OPENROUTER'
            WHEN a.endpoint_url ~* 'deepseek\.com'                    THEN 'DEEPSEEK'
            WHEN a.endpoint_url ~* 'mistral\.ai'                      THEN 'MISTRAL'
            WHEN a.endpoint_url ~* 'cohere\.'                         THEN 'COHERE'
            WHEN a.endpoint_url ~* 'generativelanguage\.googleapis\.com' THEN 'GOOGLE_GEMINI'
            WHEN a.endpoint_url ~* 'googleapis\.com'                  THEN 'GOOGLE_VERTEX'
            WHEN a.endpoint_url ~* 'localhost' OR a.endpoint_url ~* '127\.0\.0\.1' OR a.endpoint_url ~* 'ollama'
                THEN 'OLLAMA'
            ELSE 'CUSTOM'
        END AS provider_type
    FROM agents a
    WHERE a.deleted = FALSE AND a.provider_id IS NULL
),
distinct_targets AS (
    SELECT DISTINCT project_id, organization_id, provider_type, host, endpoint_url AS base_url
    FROM classified
),
inserted AS (
    INSERT INTO providers (id, version, created_at, updated_at, deleted, organization_id, project_id,
                            name, type, base_url, auth_type, health_status)
    SELECT gen_random_uuid(), 0, now(), now(), FALSE, organization_id, project_id,
           initcap(replace(provider_type, '_', ' ')) || ' (' || host || ')',
           provider_type, base_url, 'NONE', 'UNKNOWN'
    FROM distinct_targets
    RETURNING id, project_id, type, base_url
)
UPDATE agents a
SET provider_id = i.id
FROM classified c
JOIN inserted i
  ON i.project_id = c.project_id
 AND i.type = c.provider_type
 AND i.base_url = c.endpoint_url
WHERE a.id = c.agent_id;
