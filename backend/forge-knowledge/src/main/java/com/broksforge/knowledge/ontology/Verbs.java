package com.broksforge.knowledge.ontology;

import com.broksforge.kernel.api.EdgeFamily;
import com.broksforge.kernel.api.Verb;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The Knowledge System's verb catalog — every relationship verb pinned to exactly one
 * {@link EdgeFamily}.
 *
 * <p>This is the userspace discipline the Kernel Governance assigned to consumers (KAP-2, REJECTED as a
 * kernel feature): the kernel keeps no global verb→family registry, so the semantic layer keeps one, so
 * that one verb name means one family everywhere in a Forge graph.
 */
public final class Verbs {

    private Verbs() {
    }

    // composition
    public static final Verb USES = v("uses", EdgeFamily.COMPOSITION);
    public static final Verb CONTAINS = v("contains", EdgeFamily.COMPOSITION);
    public static final Verb DEPENDS_ON = v("depends_on", EdgeFamily.COMPOSITION);
    public static final Verb INCLUDES = v("includes", EdgeFamily.COMPOSITION);
    public static final Verb INDEXES = v("indexes", EdgeFamily.COMPOSITION);
    public static final Verb ENFORCES = v("enforces", EdgeFamily.COMPOSITION);

    // derivation
    public static final Verb DERIVED_FROM = v("derived_from", EdgeFamily.DERIVATION);
    public static final Verb SUPERSEDES = v("supersedes", EdgeFamily.DERIVATION);
    public static final Verb FORKED_FROM = v("forked_from", EdgeFamily.DERIVATION);
    public static final Verb FINE_TUNED_FROM = v("fine_tuned_from", EdgeFamily.DERIVATION);
    public static final Verb EXECUTED = v("executed", EdgeFamily.DERIVATION);
    public static final Verb GENERATED_BY = v("generated_by", EdgeFamily.DERIVATION);
    /** An artifact came from (was produced by) the run/process that emitted it — process provenance. */
    public static final Verb PRODUCED_BY = v("produced_by", EdgeFamily.DERIVATION);

    // evidence
    public static final Verb CITES = v("cites", EdgeFamily.EVIDENCE);
    public static final Verb SUPPORTS = v("supports", EdgeFamily.EVIDENCE);
    public static final Verb REFUTES = v("refutes", EdgeFamily.EVIDENCE);
    public static final Verb MEASURED_BY = v("measured_by", EdgeFamily.EVIDENCE);

    // causality
    public static final Verb CAUSED = v("caused", EdgeFamily.CAUSALITY);
    public static final Verb TRIGGERED = v("triggered", EdgeFamily.CAUSALITY);
    public static final Verb DETECTED_BY = v("detected_by", EdgeFamily.CAUSALITY);
    public static final Verb REGRESSED = v("regressed", EdgeFamily.CAUSALITY);

    // intent
    public static final Verb APPLIED = v("applied", EdgeFamily.INTENT);
    public static final Verb TARGETS = v("targets", EdgeFamily.INTENT);
    public static final Verb RESTS_ON = v("rests_on", EdgeFamily.INTENT);
    public static final Verb PROPOSES = v("proposes", EdgeFamily.INTENT);
    public static final Verb APPROVES = v("approves", EdgeFamily.INTENT);
    public static final Verb REJECTS = v("rejects", EdgeFamily.INTENT);

    private static final Map<String, Verb> BY_NAME = new LinkedHashMap<>();

    private static Verb v(String name, EdgeFamily family) {
        Verb verb = new Verb(name, family);
        // BY_NAME is populated lazily below to avoid static-init ordering issues; see registerAll().
        return verb;
    }

    static {
        for (Verb verb : new Verb[]{USES, CONTAINS, DEPENDS_ON, INCLUDES, INDEXES, ENFORCES,
                DERIVED_FROM, SUPERSEDES, FORKED_FROM, FINE_TUNED_FROM, EXECUTED, GENERATED_BY, PRODUCED_BY,
                CITES, SUPPORTS, REFUTES, MEASURED_BY,
                CAUSED, TRIGGERED, DETECTED_BY, REGRESSED,
                APPLIED, TARGETS, RESTS_ON, PROPOSES, APPROVES, REJECTS}) {
            BY_NAME.put(verb.name(), verb);
        }
    }

    /**
     * @param name a verb name
     * @return the catalog verb (with its one canonical family), if known
     */
    public static Optional<Verb> byName(String name) {
        return Optional.ofNullable(BY_NAME.get(name));
    }
}
