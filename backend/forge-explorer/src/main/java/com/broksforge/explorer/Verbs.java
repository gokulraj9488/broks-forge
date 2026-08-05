package com.broksforge.explorer;

import com.broksforge.kernel.api.EdgeFamily;
import com.broksforge.kernel.api.Verb;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A userspace verb catalog — the application's registry of relationship verbs, each pinned to the one
 * {@link EdgeFamily} it belongs to.
 *
 * <p>The kernel models verbs as "open data, closed families" (MANIFESTO Article III): a {@link Verb}
 * is constructed with an explicit family every time, and the kernel does not remember which family a
 * given verb <em>name</em> canonically belongs to. That registry, the {@code Verb} javadoc says,
 * "lives in the kernel core" — but it is not exposed through the public API, so every application must
 * supply its own. This class is ours. Centralizing it here is what keeps the family assignment of
 * {@code caused} (causality) or {@code cites} (evidence) consistent across the whole application; two
 * call sites can no longer disagree.
 *
 * <p>See the usability report for why this is recorded as a friction point (and a candidate kernel
 * amendment): a shared registry in userspace protects one app from itself, but nothing stops a second
 * app from pairing the same verb name with a different family in edges written to the same graph.
 */
public final class Verbs {

    private Verbs() {
    }

    // --- composition: "is built from" ---------------------------------------------------------
    /** Composition: this artifact incorporates another as a part. */
    public static final Verb USES = new Verb("uses", EdgeFamily.COMPOSITION);
    /** Composition: this artifact requires another to function. */
    public static final Verb DEPENDS_ON = new Verb("depends_on", EdgeFamily.COMPOSITION);

    // --- derivation: "came from" --------------------------------------------------------------
    /** Derivation: this revision was produced from another. */
    public static final Verb DERIVED_FROM = new Verb("derived_from", EdgeFamily.DERIVATION);
    /** Derivation: this revision replaces an older one. */
    public static final Verb SUPERSEDES = new Verb("supersedes", EdgeFamily.DERIVATION);

    // --- evidence: "is justified by" ----------------------------------------------------------
    /** Evidence: a claim cites an observation as support. */
    public static final Verb CITES = new Verb("cites", EdgeFamily.EVIDENCE);
    /** Evidence: a claim or observation contradicts another claim. */
    public static final Verb REFUTES = new Verb("refutes", EdgeFamily.EVIDENCE);

    // --- causality: "brought about" -----------------------------------------------------------
    /** Causality: one fact brought about another. */
    public static final Verb CAUSED = new Verb("caused", EdgeFamily.CAUSALITY);
    /** Causality: one fact set another in motion. */
    public static final Verb TRIGGERED = new Verb("triggered", EdgeFamily.CAUSALITY);

    // --- intent: "was chosen by" --------------------------------------------------------------
    /** Intent: a decision rests on the claim it cites as its basis (DOMAIN_MODEL §3.4 — via intent
     *  edges; this is the reference the kernel's Decision Law checks for). */
    public static final Verb RESTS_ON = new Verb("rests_on", EdgeFamily.INTENT);
    /** Intent: a decision was applied to an artifact. */
    public static final Verb APPLIED = new Verb("applied", EdgeFamily.INTENT);
    /** Intent: an artifact was chosen by a decision. */
    public static final Verb DECIDED_BY = new Verb("decided_by", EdgeFamily.INTENT);

    private static final Map<String, Verb> BY_NAME = new ConcurrentHashMap<>();

    static {
        for (Verb v : new Verb[]{USES, DEPENDS_ON, DERIVED_FROM, SUPERSEDES, CITES, RESTS_ON,
                REFUTES, CAUSED, TRIGGERED, APPLIED, DECIDED_BY}) {
            BY_NAME.put(v.name(), v);
        }
    }

    /**
     * Looks up the canonical verb for a name, so callers cannot accidentally pair a name with the
     * wrong family. This is the discipline the kernel does not provide.
     *
     * @param name the verb name
     * @return the registered verb, if the application knows it
     */
    public static Optional<Verb> byName(String name) {
        return Optional.ofNullable(BY_NAME.get(name));
    }
}
