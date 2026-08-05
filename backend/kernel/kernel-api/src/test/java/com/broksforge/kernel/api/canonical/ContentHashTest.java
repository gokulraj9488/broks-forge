package com.broksforge.kernel.api.canonical;

import com.broksforge.kernel.api.HashAlgorithm;
import com.broksforge.kernel.api.RevisionHash;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for content hashing — the realization of Law 3 (content addressing).
 *
 * <p>The strategy is two-layered so it needs no precomputed composite hash literals:
 * <ol>
 *   <li>A SHA-256 <b>known-answer test</b> on raw input (whose digest is public knowledge) proves
 *       the hashing layer is wired correctly.</li>
 *   <li>Determinism and sensitivity properties prove the canonical-content layer feeds the hasher
 *       correctly.</li>
 * </ol>
 * Together these pin content addressing without hand-computing SHA-256 over canonical JSON.
 */
class ContentHashTest {

    @Test
    @DisplayName("SHA-256 known-answer test: empty input")
    void sha256EmptyKnownAnswer() {
        RevisionHash h = ContentHash.of(new byte[0]);
        assertEquals(HashAlgorithm.SHA_256, h.algorithm());
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", h.hex());
        assertEquals("sha-256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", h.toString());
    }

    @Test
    @DisplayName("SHA-256 known-answer test: 'abc'")
    void sha256AbcKnownAnswer() {
        RevisionHash h = ContentHash.of("abc".getBytes(StandardCharsets.UTF_8));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", h.hex());
    }

    @Test
    @DisplayName("hashing a canonical value equals hashing its bytes")
    void valueEqualsBytes() {
        CanonicalValue v = CanonicalValue.objectBuilder().put("a", 1).put("b", 2).build();
        RevisionHash viaValue = ContentHash.of(v);
        RevisionHash viaBytes = ContentHash.of(CanonicalSerializer.toBytes(v));
        assertEquals(viaBytes, viaValue);
    }

    @Test
    @DisplayName("determinism: equal content -> equal hash")
    void determinism() {
        CanonicalValue a = CanonicalValue.objectBuilder().put("x", "hello").put("n", 42).build();
        CanonicalValue b = CanonicalValue.objectBuilder().put("n", 42).put("x", "hello").build();
        assertEquals(ContentHash.of(a), ContentHash.of(b), "equal content must hash equally");
    }

    @Test
    @DisplayName("sensitivity: different content -> different hash")
    void sensitivity() {
        RevisionHash a = ContentHash.of(CanonicalValue.of("a"));
        RevisionHash b = ContentHash.of(CanonicalValue.of("b"));
        assertNotEquals(a, b);
    }

    @Test
    @DisplayName("numeric canonicality flows through to the hash")
    void numericCanonicalityFlowsThrough() {
        RevisionHash one = ContentHash.of(CanonicalValue.of(new java.math.BigDecimal("1.0")));
        RevisionHash oneFlat = ContentHash.of(CanonicalValue.of(1));
        assertEquals(one, oneFlat, "1.0 and 1 must hash identically");
    }

    @Test
    @DisplayName("null argument is rejected")
    void rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> ContentHash.of((byte[]) null));
    }
}
