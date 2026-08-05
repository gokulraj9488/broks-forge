package com.broksforge.fvcs.history;

import com.broksforge.kernel.api.RevisionHash;

import java.util.List;

/**
 * The result of finding the merge base (lowest common ancestor) of two commits.
 *
 * <p>{@link Kind#SINGLE} is the normal, unambiguous case; {@link Kind#CRISS_CROSS} means the two
 * histories have more than one maximal common ancestor (a recursive/virtual-base strategy is future
 * work — FVCS never guesses); {@link Kind#NONE} means unrelated histories (no common ancestor).
 *
 * @param kind  the outcome
 * @param bases the maximal common ancestors (one for SINGLE, several for CRISS_CROSS, none for NONE)
 */
public record MergeBase(Kind kind, List<RevisionHash> bases) {

    /** The base-finding outcome. */
    public enum Kind { SINGLE, CRISS_CROSS, NONE }

    public MergeBase {
        bases = List.copyOf(bases);
    }

    /** @return the single base (only valid when {@link #kind()} is SINGLE) */
    public RevisionHash single() {
        if (kind != Kind.SINGLE) {
            throw new IllegalStateException("no single merge base: " + kind);
        }
        return bases.get(0);
    }
}
