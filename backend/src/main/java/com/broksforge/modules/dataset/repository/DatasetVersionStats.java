package com.broksforge.modules.dataset.repository;

/**
 * Aggregate statistics for a single dataset version, computed database-side via a
 * JPQL constructor expression to avoid loading items into memory. Boxed types are
 * used so the constructor matches the aggregate function return types exactly.
 *
 * @param itemCount              total rows in the version
 * @param withExpectedOutput     rows that carry an expected output (ground truth)
 * @param avgInputLength         mean character length of inputs ({@code null} if empty)
 * @param avgExpectedOutputLength mean character length of expected outputs ({@code null} if none)
 */
public record DatasetVersionStats(
        Long itemCount,
        Long withExpectedOutput,
        Double avgInputLength,
        Double avgExpectedOutputLength
) {
}
