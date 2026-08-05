package com.broksforge.kernel.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The closed kernel enums: exactly four kinds and exactly five edge families (Law 4, Article III).
 * These counts are asserted so that any future widening of a closed set fails the build — a kind
 * or family may only be added by constitutional amendment, never by an unnoticed code change.
 */
class KernelEnumsTest {

    @Test
    @DisplayName("there are exactly four kinds")
    void fourKinds() {
        assertEquals(4, Kind.values().length);
    }

    @Test
    @DisplayName("kind wire names round-trip")
    void kindWireNames() {
        for (Kind k : Kind.values()) {
            assertSame(k, Kind.fromWireName(k.wireName()));
        }
        assertEquals("artifact", Kind.ARTIFACT.wireName());
        assertThrows(IllegalArgumentException.class, () -> Kind.fromWireName("nonsense"));
        assertThrows(IllegalArgumentException.class, () -> Kind.fromWireName(null));
    }

    @Test
    @DisplayName("there are exactly five edge families")
    void fiveFamilies() {
        assertEquals(5, EdgeFamily.values().length);
    }

    @Test
    @DisplayName("edge family wire names round-trip")
    void familyWireNames() {
        for (EdgeFamily f : EdgeFamily.values()) {
            assertSame(f, EdgeFamily.fromWireName(f.wireName()));
        }
        assertEquals("composition", EdgeFamily.COMPOSITION.wireName());
        assertThrows(IllegalArgumentException.class, () -> EdgeFamily.fromWireName("nonsense"));
    }

    @Test
    @DisplayName("verbs are open but must be well-formed and carry a family")
    void verbs() {
        Verb uses = new Verb("uses", EdgeFamily.COMPOSITION);
        assertEquals("uses", uses.name());
        assertEquals(EdgeFamily.COMPOSITION, uses.family());

        assertThrows(IllegalArgumentException.class, () -> new Verb("Uses", EdgeFamily.COMPOSITION));
        assertThrows(IllegalArgumentException.class, () -> new Verb("1bad", EdgeFamily.COMPOSITION));
        assertThrows(IllegalArgumentException.class, () -> new Verb("has space", EdgeFamily.COMPOSITION));
        assertThrows(IllegalArgumentException.class, () -> new Verb("ok", null));
        assertThrows(IllegalArgumentException.class, () -> new Verb(null, EdgeFamily.INTENT));
    }
}
