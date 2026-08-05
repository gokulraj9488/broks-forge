package com.broksforge.fvcs.ontology;

import com.broksforge.kernel.api.EdgeFamily;
import com.broksforge.kernel.api.Verb;

/**
 * The version-control verbs FVCS adds — each pinned to exactly one kernel edge family, following the
 * userspace verb-catalog discipline (KAP-2). These are additive registry data contributed through the
 * knowledge system's public SPI; they change neither the kernel nor the knowledge system.
 */
public final class FvcsVerbs {

    private FvcsVerbs() {
    }

    /** Derivation: a commit came from its parent commit(s) — the commit DAG (a merge has ≥2). */
    public static final Verb PARENT = new Verb("parent", EdgeFamily.DERIVATION);
    /** Composition: a commit is built from the snapshot (tree) it checkpoints. */
    public static final Verb RECORDS = new Verb("records", EdgeFamily.COMPOSITION);
    /** Intent: a tag was chosen to name a particular commit. */
    public static final Verb MARKS = new Verb("marks", EdgeFamily.INTENT);
}
