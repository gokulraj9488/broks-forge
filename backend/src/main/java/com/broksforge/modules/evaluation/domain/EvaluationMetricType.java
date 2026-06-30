package com.broksforge.modules.evaluation.domain;

/**
 * The catalogue of supported metric kinds. Each value is scored by the
 * {@code EvaluationMetricEngine}. Adding a metric is a code-only change: add a
 * constant here and a branch in the engine — no schema migration is required, since
 * metric results are stored generically.
 *
 * <p>The {@link Category} drives regression analysis (quality vs latency vs cost
 * regressions — see the regression module).</p>
 */
public enum EvaluationMetricType {

    /** Output equals the expected output (optionally case-insensitive, trimmed). */
    EXACT_MATCH(Category.QUALITY),
    /** Output contains a substring (params.value, or the expected output). */
    CONTAINS(Category.QUALITY),
    /** Output matches a regular expression (params.pattern). */
    REGEX_MATCH(Category.QUALITY),
    /** Output parses as well-formed JSON. */
    JSON_VALID(Category.QUALITY),
    /** Output is non-blank. */
    NON_EMPTY(Category.QUALITY),
    /** Output length is within [params.min, params.max] characters. */
    LENGTH(Category.QUALITY),
    /** Invocation latency is within threshold milliseconds. */
    LATENCY(Category.PERFORMANCE),
    /** Invocation cost is within threshold. */
    COST(Category.COST),
    /** Total token usage is within threshold. */
    TOKEN_COUNT(Category.COST);

    public enum Category {
        QUALITY,
        PERFORMANCE,
        COST
    }

    private final Category category;

    EvaluationMetricType(Category category) {
        this.category = category;
    }

    public Category category() {
        return category;
    }
}
