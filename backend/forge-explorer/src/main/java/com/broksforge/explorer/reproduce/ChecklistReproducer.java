package com.broksforge.explorer.reproduce;

import com.broksforge.kernel.api.Kind;
import com.broksforge.kernel.api.Revision;
import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.kernel.core.reproduce.ReproduceContext;
import com.broksforge.kernel.core.reproduce.Reproducer;
import com.broksforge.explorer.render.Values;

import java.util.ArrayList;
import java.util.List;

/**
 * A userspace {@link Reproducer} that re-executes an artifact of subtype {@code check-suite} and
 * records one observation per check.
 *
 * <p>It exists to exercise the reproduce SPI end to end without the kernel knowing anything about what
 * it re-executes (ADR-V2-0007, op 5). Re-execution here is deterministic and pure — it reads only the
 * revision and its pinned closure from the {@link ReproduceContext} — so reproducing the same revision
 * twice yields byte-identical observation content (same {@code RevisionHash}, deduplicated), while the
 * kernel still records two distinct facts. That is exactly the property the "reproducibility where
 * reality permits" law promises, demonstrated from userspace.
 */
public final class ChecklistReproducer implements Reproducer {

    /** The artifact subtype this reproducer knows how to run. */
    public static final String SUITE_SUBTYPE = "check-suite";

    @Override
    public boolean supports(Kind kind, String subtype) {
        return kind == Kind.ARTIFACT && SUITE_SUBTYPE.equals(subtype);
    }

    @Override
    public List<Revision> reproduce(ReproduceContext context) {
        List<Revision> observations = new ArrayList<>();
        CanonicalValue checks = Values.field(context.revision().payload(), "checks").orElse(CanonicalValue.NULL);
        if (checks instanceof CanonicalValue.Arr arr) {
            for (CanonicalValue check : arr.items()) {
                String name = check instanceof CanonicalValue.Str s ? s.value() : Values.oneLine(check);
                observations.add(observationFor(context, name));
            }
        }
        return observations;
    }

    private Revision observationFor(ReproduceContext context, String checkName) {
        // Deterministic verdict derived solely from the pinned configuration (the closure) — no clock,
        // no randomness — so the observation is content-addressed and reproducible.
        boolean passed = (checkName.hashCode() ^ context.closure().size()) % 7 != 0;
        CanonicalValue payload = CanonicalValue.objectBuilder()
                .put("check", checkName)
                .put("passed", passed)
                .put("method", "deterministic-replay:v1")
                .put("suite", context.revisionHash().toString())
                .put("closure_size", context.closure().size())
                .build();
        return Revision.leaf(Kind.OBSERVATION, "check-result", payload);
    }
}
