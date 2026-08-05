package com.broksforge.kernel.api;

import java.util.Locale;

/**
 * The five edge families — the closed set of relationship semantics in the Forge Graph.
 *
 * <p>Relationship verbs are open (see {@link Verb}), but every verb belongs to exactly one of
 * these families, and the family — not the verb — carries the semantics that traversal, closure,
 * and law-checking rely on. A sixth family would be a constitutional amendment
 * (MANIFESTO Article X); the claim of the constitution is that none exists in engineering.
 *
 * <ul>
 *   <li>{@link #COMPOSITION} — "is built from": closures, agent DNA, blast radius.</li>
 *   <li>{@link #DERIVATION} — "came from": lineage, version history.</li>
 *   <li>{@link #EVIDENCE} — "is justified by": the Claim law, every "how do you know?".</li>
 *   <li>{@link #CAUSALITY} — "brought about": root cause, failure propagation.</li>
 *   <li>{@link #INTENT} — "was chosen by": decision replay, accountability.</li>
 * </ul>
 *
 * <p>Implements MANIFESTO Article III; justified by docs/v2/DOMAIN_MODEL.md §4.2.
 */
public enum EdgeFamily {

    COMPOSITION,
    DERIVATION,
    EVIDENCE,
    CAUSALITY,
    INTENT;

    /**
     * The stable lower-case token for this family in serialized form.
     *
     * @return the lower-case wire name (e.g. {@code "composition"})
     */
    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Parses a wire name back into a family.
     *
     * @param wireName the lower-case token, as produced by {@link #wireName()}
     * @return the matching family
     * @throws IllegalArgumentException if the token is null or not a known family
     */
    public static EdgeFamily fromWireName(String wireName) {
        if (wireName == null) {
            throw new IllegalArgumentException("edge family wire name must not be null");
        }
        for (EdgeFamily f : values()) {
            if (f.wireName().equals(wireName)) {
                return f;
            }
        }
        throw new IllegalArgumentException("unknown edge family: " + wireName);
    }
}
