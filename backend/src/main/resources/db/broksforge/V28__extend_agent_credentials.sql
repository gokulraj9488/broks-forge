-- ===========================================================================
-- V28 — Complete credential management
-- ---------------------------------------------------------------------------
-- Adds first-class support for a human label, a configurable header prefix
-- (e.g. "Bearer", "Token"), and the outcome of the most recent connection test.
-- All columns are nullable so existing credentials remain valid unchanged;
-- BEARER_TOKEN still defaults to the "Bearer" prefix when none is stored.
-- Secrets remain write-only and encrypted — no plaintext is ever added here.
-- ===========================================================================

ALTER TABLE agent_credentials
    ADD COLUMN label                 VARCHAR(120),
    ADD COLUMN header_prefix         VARCHAR(64),
    ADD COLUMN last_tested_at        TIMESTAMPTZ,
    ADD COLUMN last_test_success     BOOLEAN,
    ADD COLUMN last_test_http_status INTEGER,
    ADD COLUMN last_test_message     VARCHAR(1000);
