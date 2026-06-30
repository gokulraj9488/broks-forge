package com.broksforge.modules.evaluation.service;

import java.math.BigDecimal;

/**
 * Per-run roll-up returned from persistence so the executor can aggregate the job
 * summary without re-reading rows.
 */
public record RunTotals(
        boolean success,
        boolean overallPassed,
        BigDecimal score,
        Long latencyMs,
        Integer totalTokens,
        BigDecimal cost
) {
}
