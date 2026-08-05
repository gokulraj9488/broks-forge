package com.broksforge.kernel.api;

import java.util.UUID;

/**
 * The continuant identity of a node — the stable, opaque handle a biography is about.
 *
 * <p>A continuant (e.g. <em>the</em> support agent) has one {@code NodeId} for its whole life and
 * a chain of immutable revisions that share it. Identity is deliberately <b>opaque and
 * assigned</b>, not content-derived: a thing must be able to change while remaining the same
 * thing. This is the sharpest distinction in the identity model — {@code NodeId} answers "which
 * thing?", {@link RevisionHash} answers "which state?", and the two are never conflated
 * (docs/v2/DOMAIN_MODEL.md §1).
 *
 * <p><b>Minting is not done here.</b> Allocating a fresh, unique {@code NodeId} at a continuant's
 * first append is the append engine's responsibility (kernel core), because it requires a source
 * of uniqueness. This value type deliberately contains no random or clock-based factory, keeping
 * {@code kernel-api} free of non-determinism.
 *
 * @param value the underlying UUID; never null
 */
public record NodeId(UUID value) {

    /**
     * @throws IllegalArgumentException if {@code value} is null
     */
    public NodeId {
        if (value == null) {
            throw new IllegalArgumentException("node id must not be null");
        }
    }

    /**
     * Wraps an existing UUID as a {@code NodeId}.
     *
     * @param value the UUID
     * @return the node id
     */
    public static NodeId of(UUID value) {
        return new NodeId(value);
    }

    /**
     * Parses the canonical string form (a UUID) into a {@code NodeId}.
     *
     * @param s the UUID text
     * @return the parsed node id
     * @throws IllegalArgumentException if {@code s} is null or not a valid UUID
     */
    public static NodeId fromString(String s) {
        if (s == null) {
            throw new IllegalArgumentException("node id text must not be null");
        }
        try {
            return new NodeId(UUID.fromString(s));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid node id: " + s, e);
        }
    }

    /** @return the canonical UUID string */
    @Override
    public String toString() {
        return value.toString();
    }
}
