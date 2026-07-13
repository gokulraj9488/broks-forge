package com.broksforge.modules.evaluation.domain;

/**
 * The catalogue of supported metric kinds. Each value is scored by an
 * {@code EvaluationMetric} implementation registered with {@code EvaluationMetricEngine}
 * (a plugin registry, not a switch) — adding a new metric to this fixed catalogue means
 * adding a constant here and one {@code @Component} implementation, never a change to the
 * engine itself. {@link #CUSTOM} is the one type that needs no enum change at all: it
 * dispatches by {@code params.key} to a named {@code CustomMetricEvaluator} bean.
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
    /** Output parses as well-formed JSON; validates against params.schema when present. */
    JSON_VALID(Category.QUALITY),
    /** Output is non-blank. */
    NON_EMPTY(Category.QUALITY),
    /** Output length is within [params.min, params.max] characters. */
    LENGTH(Category.QUALITY),
    /** Embedding cosine similarity between output and expected output (params.providerId, params.embeddingModel). */
    SEMANTIC_SIMILARITY(Category.QUALITY),
    /** A judge model scores the output 0.0-1.0 against a rubric (params.providerId, params.model, params.rubric). */
    LLM_JUDGE(Category.QUALITY),
    /** A judge model flags output claims unsupported by params.context/expectedOutput/input. */
    HALLUCINATION_DETECTION(Category.QUALITY),
    /** A judge model checks output citations are consistent with params.context/expectedOutput. */
    CITATION_VERIFICATION(Category.QUALITY),
    /** Dispatches by params.key to a named CustomMetricEvaluator bean — the true no-enum-change extension point. */
    CUSTOM(Category.QUALITY),
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
