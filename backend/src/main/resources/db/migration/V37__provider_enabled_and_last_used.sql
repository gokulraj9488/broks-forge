-- ===========================================================================
-- V37 — Provider Platform v2, Feature 2 (Provider Management): enable/disable
-- and last-used tracking.
-- ---------------------------------------------------------------------------
-- Purely additive: two nullable/defaulted columns, no existing column altered.
-- enabled defaults TRUE so every existing provider (including everything the
-- V36 backfill created) remains exactly as usable as before this migration.
-- ===========================================================================

ALTER TABLE providers
    ADD COLUMN enabled      BOOLEAN     NOT NULL DEFAULT TRUE,
    ADD COLUMN last_used_at TIMESTAMPTZ;

CREATE INDEX idx_providers_enabled ON providers (enabled);
