package com.broksforge.kernel.api;

import java.util.Locale;

/**
 * The four kernel kinds — the closed set of epistemic statuses an engineering fact can have.
 *
 * <p>Every node in the Forge Graph is exactly one of these, and the set is closed: a new kind
 * would be a constitutional amendment (MANIFESTO Article X), never a code change. Kinds are
 * distinguished not by subject matter but by their <em>law of revision</em> — the relationship a
 * fact has to truth — which is why they cannot merge:
 *
 * <ul>
 *   <li>{@link #ARTIFACT} — intent; what was designed. Superseded by new revisions.</li>
 *   <li>{@link #OBSERVATION} — reality; what happened. Never revised, only annotated.</li>
 *   <li>{@link #CLAIM} — belief; what it is thought to mean. Superseded by better reasoning.</li>
 *   <li>{@link #DECISION} — will; what was chosen. Never unmade, only followed.</li>
 * </ul>
 *
 * <p>Implements MANIFESTO Article II and Law 4 (Epistemic partition); justified by ADR-V2-0002.
 */
public enum Kind {

    ARTIFACT,
    OBSERVATION,
    CLAIM,
    DECISION;

    /**
     * The stable lower-case token used for this kind in addresses and serialized form. Stable
     * across releases: it participates in {@link Address} URIs, which must round-trip forever.
     *
     * @return the lower-case wire name (e.g. {@code "artifact"})
     */
    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Parses a wire name back into a kind.
     *
     * @param wireName the lower-case token, as produced by {@link #wireName()}
     * @return the matching kind
     * @throws IllegalArgumentException if the token is null or not a known kind
     */
    public static Kind fromWireName(String wireName) {
        if (wireName == null) {
            throw new IllegalArgumentException("kind wire name must not be null");
        }
        for (Kind k : values()) {
            if (k.wireName().equals(wireName)) {
                return k;
            }
        }
        throw new IllegalArgumentException("unknown kind: " + wireName);
    }
}
