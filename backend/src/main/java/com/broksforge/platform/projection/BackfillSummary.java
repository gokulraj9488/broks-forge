package com.broksforge.platform.projection;

/** Counts of artifacts projected by a backfill pass (idempotent: re-running yields the same artifacts). */
public record BackfillSummary(int providers, int models, int prompts) {

    public int total() {
        return providers + models + prompts;
    }
}
