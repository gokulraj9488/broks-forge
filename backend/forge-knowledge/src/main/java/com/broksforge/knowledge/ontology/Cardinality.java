package com.broksforge.knowledge.ontology;

/**
 * How many times a relationship may (or must) appear from one source object.
 *
 * <p>Cardinality is what turns the kernel's "any number of edges" into ontology invariants such as
 * "an Agent uses exactly one Model" (CI-4) or "a Claim cites one or more observations" (Claim law).
 */
public enum Cardinality {

    /** Exactly one occurrence is required. */
    EXACTLY_ONE(1, 1),
    /** Zero or one. */
    ZERO_OR_ONE(0, 1),
    /** One or more are required. */
    ONE_OR_MORE(1, Integer.MAX_VALUE),
    /** Two or more are required (e.g. an Experiment's variants). */
    AT_LEAST_TWO(2, Integer.MAX_VALUE),
    /** Any number, including zero. */
    ANY(0, Integer.MAX_VALUE);

    private final int min;
    private final int max;

    Cardinality(int min, int max) {
        this.min = min;
        this.max = max;
    }

    /** @return the minimum required occurrences */
    public int min() {
        return min;
    }

    /** @return the maximum allowed occurrences */
    public int max() {
        return max;
    }

    /**
     * @param count an observed occurrence count
     * @return true if {@code count} satisfies this cardinality
     */
    public boolean allows(int count) {
        return count >= min && count <= max;
    }
}
