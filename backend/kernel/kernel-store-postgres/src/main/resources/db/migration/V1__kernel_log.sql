-- Forge Kernel persistence schema, version 1.
--
-- The append log is the sole source of truth (ADR-V2-0001). Only the log is persisted; the
-- revision store, graph index, and name store are projections rebuilt in memory by replaying this
-- table at startup. This keeps the schema minimal and the durability guarantee unambiguous: if the
-- log survives, the entire graph can be reconstructed.
--
-- Each row is one immutable, hash-chained fact. (org, position) is the gapless per-org total order;
-- the primary key rejects any duplicate position, which is the last line of defence against two
-- writers racing for the same slot. entry_json is the full canonical encoding (LogEntryCodec),
-- carrying complete content so projections are regenerable from this column alone.

CREATE TABLE kernel_log (
    org        TEXT   NOT NULL,
    position   BIGINT NOT NULL,
    entry_hash TEXT   NOT NULL,
    entry_json TEXT   NOT NULL,
    PRIMARY KEY (org, position)
);

-- Fast "latest entry for an org" lookups (position assignment and chain head).
CREATE INDEX kernel_log_org_position_desc ON kernel_log (org, position DESC);
