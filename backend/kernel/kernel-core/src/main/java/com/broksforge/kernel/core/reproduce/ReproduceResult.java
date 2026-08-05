package com.broksforge.kernel.core.reproduce;

import com.broksforge.kernel.api.Address;

import java.util.List;

/**
 * The outcome of a {@code reproduce}: whether a reproducer ran, the observation addresses it
 * produced, and a human-readable detail. When no reproducer supports the revision — which is also the
 * correct answer for an observation, since reality is not replayable — {@code reproduced} is false and
 * {@code observations} is empty.
 *
 * @param reproduced   true if a reproducer ran
 * @param observations the addresses of the recorded observation revisions (empty if none)
 * @param detail       a human-readable explanation (which reproducer, or why none)
 */
public record ReproduceResult(boolean reproduced, List<Address> observations, String detail) {

    public ReproduceResult {
        observations = List.copyOf(observations);
        if (detail == null) {
            throw new IllegalArgumentException("detail must not be null");
        }
    }

    /**
     * @param detail why nothing was reproduced
     * @return a not-reproduced result
     */
    public static ReproduceResult notReproducible(String detail) {
        return new ReproduceResult(false, List.of(), detail);
    }
}
