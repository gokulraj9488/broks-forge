package com.broksforge.fvcs.repo;

import com.broksforge.fvcs.ontology.FvcsTypes;

/**
 * A tag's role (a payload value, not a subtype — KN-0002). A {@link #RELEASE} tag is a blessed immutable
 * snapshot for external consumption; a {@link #BASELINE} tag is a named reference point for comparison;
 * a {@link #LIGHTWEIGHT} tag is a plain immovable name.
 */
public enum TagRole {

    LIGHTWEIGHT(FvcsTypes.ROLE_LIGHTWEIGHT),
    RELEASE(FvcsTypes.ROLE_RELEASE),
    BASELINE(FvcsTypes.ROLE_BASELINE);

    private final String wire;

    TagRole(String wire) {
        this.wire = wire;
    }

    /** @return the payload token for this role */
    public String wire() {
        return wire;
    }
}
