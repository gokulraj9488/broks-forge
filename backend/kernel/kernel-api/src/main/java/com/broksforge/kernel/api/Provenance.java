package com.broksforge.kernel.api;

import java.time.Instant;

/**
 * The provenance stamped on every append: who, and the two times (Law 2 and Law 8).
 *
 * <p>Every fact records the actor that asserted it (Law 2, total provenance) and is bitemporal
 * (Law 8): <b>valid time</b> is when the fact was true in the world, <b>record time</b> is when
 * Forge recorded it. The pair is what makes "what did we know on Tuesday?" answerable
 * (docs/v2/DOMAIN_MODEL.md §2).
 *
 * <p>Provenance lives on the <em>fact</em> (the append), never inside a revision's content hash —
 * which is exactly why two actors asserting byte-identical content produce one {@link RevisionHash}
 * but two distinct facts (DOMAIN_MODEL §6). There is no {@code now()} factory: obtaining the record
 * time requires a clock, and the kernel keeps clocks out of these value types; the append engine
 * supplies record time.
 *
 * @param actor      the signer of the append; never null
 * @param validTime  when the fact was true in the world; never null
 * @param recordTime when Forge recorded it; never null
 */
public record Provenance(ActorId actor, Instant validTime, Instant recordTime) {

    /**
     * @throws IllegalArgumentException if any component is null
     */
    public Provenance {
        if (actor == null) {
            throw new IllegalArgumentException("provenance actor must not be null");
        }
        if (validTime == null) {
            throw new IllegalArgumentException("provenance valid time must not be null");
        }
        if (recordTime == null) {
            throw new IllegalArgumentException("provenance record time must not be null");
        }
    }

    /**
     * @param actor      the signer
     * @param validTime  when the fact was true in the world
     * @param recordTime when Forge recorded it
     * @return the provenance
     */
    public static Provenance of(ActorId actor, Instant validTime, Instant recordTime) {
        return new Provenance(actor, validTime, recordTime);
    }
}
