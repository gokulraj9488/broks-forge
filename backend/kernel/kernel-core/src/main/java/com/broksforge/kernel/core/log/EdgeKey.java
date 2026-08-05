package com.broksforge.kernel.core.log;

import com.broksforge.kernel.api.Address;
import com.broksforge.kernel.api.Verb;

/**
 * The identity of an extrinsic edge: a typed, directed relationship between two addresses.
 *
 * <p>Extrinsic edges are relationships asserted <em>about</em> nodes after the fact (causality
 * asserted by a claim, for example), as opposed to the intrinsic {@link com.broksforge.kernel.api.Ref}s
 * that are part of a revision's content. An edge's {@link com.broksforge.kernel.api.EdgeFamily} is
 * carried by its {@link Verb}. See docs/v2/DOMAIN_MODEL.md §4.
 *
 * @param from the source address
 * @param verb the relationship verb (and, through it, the family)
 * @param to   the target address
 */
public record EdgeKey(Address from, Verb verb, Address to) {

    /**
     * @throws IllegalArgumentException if any component is null
     */
    public EdgeKey {
        if (from == null) {
            throw new IllegalArgumentException("edge 'from' must not be null");
        }
        if (verb == null) {
            throw new IllegalArgumentException("edge verb must not be null");
        }
        if (to == null) {
            throw new IllegalArgumentException("edge 'to' must not be null");
        }
    }
}
