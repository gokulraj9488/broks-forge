package com.broksforge.kernel.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Value-type tests for {@link ActorId} and {@link Provenance} — the who and the two times that
 * every append carries (Law 2, Law 8).
 */
class ProvenanceTypesTest {

    @Test
    @DisplayName("ActorId accepts disciplined tokens and rejects bad ones")
    void actorId() {
        assertEquals("system:ci", ActorId.of("system:ci").value());
        assertEquals("program:nightly-pass", ActorId.of("program:nightly-pass").toString());
        // A UUID-style human token is fine.
        assertEquals("33333333-3333-3333-3333-333333333333",
                ActorId.of("33333333-3333-3333-3333-333333333333").value());

        assertThrows(IllegalArgumentException.class, () -> ActorId.of(null));
        assertThrows(IllegalArgumentException.class, () -> ActorId.of("  "));
        assertThrows(IllegalArgumentException.class, () -> ActorId.of("has space"));
        assertThrows(IllegalArgumentException.class, () -> ActorId.of("tab\tinside"));
        assertThrows(IllegalArgumentException.class, () -> ActorId.of("x".repeat(257)));
    }

    @Test
    @DisplayName("Provenance requires actor and both times")
    void provenance() {
        ActorId actor = ActorId.of("system:ci");
        Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2026-01-01T00:00:01Z");
        Provenance p = Provenance.of(actor, t1, t2);
        assertEquals(actor, p.actor());
        assertEquals(t1, p.validTime());
        assertEquals(t2, p.recordTime());

        assertThrows(IllegalArgumentException.class, () -> Provenance.of(null, t1, t2));
        assertThrows(IllegalArgumentException.class, () -> Provenance.of(actor, null, t2));
        assertThrows(IllegalArgumentException.class, () -> Provenance.of(actor, t1, null));
    }
}
