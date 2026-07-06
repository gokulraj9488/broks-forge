-- ===========================================================================
-- V29 — Provider-aware health checks
-- ---------------------------------------------------------------------------
-- Records HOW each health check probed the agent (strategy + effective URL), so
-- the history is transparent: Spring Boot → /actuator/health, FastAPI/LangGraph
-- → /health, LLM-provider agents → a POST completion, others → the raw endpoint.
-- Both columns are nullable so historical rows remain valid unchanged.
-- ===========================================================================

ALTER TABLE agent_health_checks
    ADD COLUMN probe_strategy VARCHAR(32),
    ADD COLUMN probe_url      VARCHAR(2048);
