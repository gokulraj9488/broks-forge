package com.broksforge.kernel.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Value-type tests for {@link Name} — the only mutable concept's immutable path half (ADR-V2-0006).
 */
class NameTest {

    @Test
    @DisplayName("accepts valid single- and multi-segment paths")
    void validPaths() {
        assertEquals("prod", Name.of("prod").path());
        assertEquals(List.of("agents", "support", "current"),
                Name.of("agents/support/current").segments());
        assertEquals(List.of("suites", "nightly"), Name.of("suites/nightly").segments());
        // The segment 'name' is allowed in a Name; only Address reserves it as a discriminator.
        assertEquals(List.of("name", "foo"), Name.of("name/foo").segments());
    }

    @Test
    @DisplayName("rejects malformed paths")
    void invalidPaths() {
        assertThrows(IllegalArgumentException.class, () -> Name.of(null));
        assertThrows(IllegalArgumentException.class, () -> Name.of(""));
        assertThrows(IllegalArgumentException.class, () -> Name.of("/leading"));
        assertThrows(IllegalArgumentException.class, () -> Name.of("trailing/"));
        assertThrows(IllegalArgumentException.class, () -> Name.of("a//b"));
        assertThrows(IllegalArgumentException.class, () -> Name.of("a/../b"));
        assertThrows(IllegalArgumentException.class, () -> Name.of("a/./b"));
        assertThrows(IllegalArgumentException.class, () -> Name.of("has space"));
        assertThrows(IllegalArgumentException.class, () -> Name.of("-startsWithDash"));
    }

    @Test
    @DisplayName("toString is the path")
    void stringForm() {
        assertEquals("agents/support", Name.of("agents/support").toString());
    }
}
