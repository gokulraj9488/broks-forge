package com.broksforge.kernel.api;

import java.util.UUID;

/**
 * The identity of an organization — the boundary of exactly one Forge Graph.
 *
 * <p>Each organization has one append log, one namespace, and one membership; cross-organization
 * sharing (if it ever exists) is export/import of subgraphs, never a shared graph
 * (docs/v2/DOMAIN_MODEL.md §6). This value object is the tenant boundary that scopes every
 * {@link Address}, {@link Name}, and {@link LogPosition}.
 *
 * @param value the underlying UUID; never null
 */
public record OrgId(UUID value) {

    /**
     * @throws IllegalArgumentException if {@code value} is null
     */
    public OrgId {
        if (value == null) {
            throw new IllegalArgumentException("org id must not be null");
        }
    }

    /**
     * Wraps an existing UUID as an {@code OrgId}.
     *
     * @param value the UUID
     * @return the org id
     */
    public static OrgId of(UUID value) {
        return new OrgId(value);
    }

    /**
     * Parses the canonical string form (a UUID) into an {@code OrgId}.
     *
     * @param s the UUID text
     * @return the parsed org id
     * @throws IllegalArgumentException if {@code s} is null or not a valid UUID
     */
    public static OrgId fromString(String s) {
        if (s == null) {
            throw new IllegalArgumentException("org id text must not be null");
        }
        try {
            return new OrgId(UUID.fromString(s));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid org id: " + s, e);
        }
    }

    /** @return the canonical UUID string */
    @Override
    public String toString() {
        return value.toString();
    }
}
