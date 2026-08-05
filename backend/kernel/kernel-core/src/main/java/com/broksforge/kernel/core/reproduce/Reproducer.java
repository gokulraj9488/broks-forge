package com.broksforge.kernel.core.reproduce;

import com.broksforge.kernel.api.Kind;
import com.broksforge.kernel.api.Revision;

import java.util.List;

/**
 * The reproduce SPI (ADR-V2-0007, op 5) — how userspace supplies re-execution semantics without the
 * kernel knowing what it is re-executing.
 *
 * <p>The kernel orchestrates {@code reproduce}: it resolves a revision, computes its pinned closure,
 * and — if a reproducer declares support for the revision's {@link Kind} and subtype — invokes it and
 * records the returned observations as new facts. The kernel supplies the <em>protocol and the
 * guarantees</em>; the reproducer supplies the <em>execution</em>. This is exactly why the kernel
 * stays free of AI (or any executor): an executable could be a shell script, a SQL query, or a model
 * call — the kernel neither knows nor cares.
 */
public interface Reproducer {

    /**
     * @param kind    the revision's kind
     * @param subtype the revision's subtype
     * @return true if this reproducer can re-execute revisions of this kind/subtype
     */
    boolean supports(Kind kind, String subtype);

    /**
     * Re-executes the revision described by {@code context} and returns the resulting observations.
     * Returning observations does not append them — the kernel records them, linking each back to the
     * source revision.
     *
     * @param context the revision, its hash, its closure, and its location
     * @return the observation revisions produced (may be empty)
     */
    List<Revision> reproduce(ReproduceContext context);
}
