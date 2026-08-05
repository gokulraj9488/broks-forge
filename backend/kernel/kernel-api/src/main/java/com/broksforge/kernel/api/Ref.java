package com.broksforge.kernel.api;

/**
 * An intrinsic reference from a revision to another revision, pinned by content hash.
 *
 * <p>A {@code Ref} is part of a {@link Revision}'s content: it is a typed, hash-pinned pointer to
 * the revision this one references (a composition child, a derivation parent, a claim's evidence,
 * a decision's cited claim). Because it pins the target by {@link RevisionHash}, references are
 * Merkle links — which is what makes closures content-addressed and acyclic by construction
 * (a target hash must already exist to be referenced). See docs/v2/KERNEL_IMPLEMENTATION_PLAN.md §7
 * on intrinsic-versus-extrinsic edges.
 *
 * <p>The reference's {@link EdgeFamily} is carried by its {@link Verb} ({@link #family()}).
 *
 * @param verb   the relationship verb (and, through it, the family)
 * @param target the referenced revision, pinned by hash
 */
public record Ref(Verb verb, RevisionHash target) {

    /**
     * @throws IllegalArgumentException if either component is null
     */
    public Ref {
        if (verb == null) {
            throw new IllegalArgumentException("ref verb must not be null");
        }
        if (target == null) {
            throw new IllegalArgumentException("ref target must not be null");
        }
    }

    /**
     * @param verb   the relationship verb
     * @param target the referenced revision
     * @return the reference
     */
    public static Ref of(Verb verb, RevisionHash target) {
        return new Ref(verb, target);
    }

    /** @return the edge family this reference belongs to */
    public EdgeFamily family() {
        return verb.family();
    }
}
