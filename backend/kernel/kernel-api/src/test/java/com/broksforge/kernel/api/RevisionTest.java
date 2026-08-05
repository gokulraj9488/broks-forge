package com.broksforge.kernel.api;

import com.broksforge.kernel.api.canonical.CanonicalValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link Revision} and {@link Ref} — the content-addressed node value (Law 3) and its
 * Merkle references. The central guarantees: equal content hashes equally (dedup), any change to
 * content changes the hash (sensitivity), and reference order is significant.
 */
class RevisionTest {

    private static RevisionHash targetHash(int fill) {
        byte[] b = new byte[32];
        java.util.Arrays.fill(b, (byte) fill);
        return RevisionHash.of(HashAlgorithm.SHA_256, b);
    }

    private static Revision prompt(String text) {
        return Revision.leaf(Kind.ARTIFACT, "prompt",
                CanonicalValue.objectBuilder().put("text", text).build());
    }

    @Test
    @DisplayName("equal content hashes equally (dedup); equals/hashCode are content-based")
    void dedup() {
        Revision a = prompt("hello");
        Revision b = prompt("hello");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(a.hash(), b.hash());
    }

    @Test
    @DisplayName("different content -> different hash (kind, subtype, payload, refs all matter)")
    void sensitivity() {
        Revision base = prompt("hello");
        assertNotEquals(base.hash(), prompt("world").hash());
        assertNotEquals(base.hash(),
                Revision.leaf(Kind.OBSERVATION, "prompt",
                        CanonicalValue.objectBuilder().put("text", "hello").build()).hash());
        assertNotEquals(base.hash(),
                Revision.leaf(Kind.ARTIFACT, "system-prompt",
                        CanonicalValue.objectBuilder().put("text", "hello").build()).hash());
        Revision withRef = Revision.of(Kind.ARTIFACT, "prompt",
                CanonicalValue.objectBuilder().put("text", "hello").build(),
                List.of(Ref.of(new Verb("uses", EdgeFamily.COMPOSITION), targetHash(0x01))));
        assertNotEquals(base.hash(), withRef.hash());
    }

    @Test
    @DisplayName("reference order is significant")
    void refOrderSignificant() {
        Ref r1 = Ref.of(new Verb("uses", EdgeFamily.COMPOSITION), targetHash(0x01));
        Ref r2 = Ref.of(new Verb("uses", EdgeFamily.COMPOSITION), targetHash(0x02));
        Revision ab = Revision.of(Kind.ARTIFACT, "agent", CanonicalValue.NULL, List.of(r1, r2));
        Revision ba = Revision.of(Kind.ARTIFACT, "agent", CanonicalValue.NULL, List.of(r2, r1));
        assertNotEquals(ab.hash(), ba.hash());
    }

    @Test
    @DisplayName("canonical form carries kind, subtype, payload, and refs")
    void canonicalForm() {
        Revision r = Revision.of(Kind.CLAIM, "score",
                CanonicalValue.objectBuilder().put("value", 1).build(),
                List.of(Ref.of(new Verb("cites", EdgeFamily.EVIDENCE), targetHash(0xAA))));
        String form = com.broksforge.kernel.api.canonical.CanonicalSerializer
                .toCanonicalString(r.canonicalForm());
        // Keys are sorted: family, kind, payload, refs, subtype, target, value, verb.
        assertEquals(
                "{\"kind\":\"claim\",\"payload\":{\"value\":1},"
                        + "\"refs\":[{\"family\":\"evidence\",\"target\":\"" + targetHash(0xAA)
                        + "\",\"verb\":\"cites\"}],\"subtype\":\"score\"}",
                form);
    }

    @Test
    @DisplayName("Ref exposes its family via its verb")
    void refFamily() {
        Ref r = Ref.of(new Verb("caused", EdgeFamily.CAUSALITY), targetHash(0x05));
        assertEquals(EdgeFamily.CAUSALITY, r.family());
        assertThrows(IllegalArgumentException.class, () -> Ref.of(null, targetHash(0x05)));
        assertThrows(IllegalArgumentException.class,
                () -> Ref.of(new Verb("x", EdgeFamily.INTENT), null));
    }

    @Test
    @DisplayName("validation rejects bad kind, subtype, payload, and refs")
    void validation() {
        CanonicalValue payload = CanonicalValue.NULL;
        assertThrows(IllegalArgumentException.class,
                () -> Revision.of(null, "prompt", payload, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> Revision.of(Kind.ARTIFACT, "Bad-UPPER", payload, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> Revision.of(Kind.ARTIFACT, "prompt", null, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> Revision.of(Kind.ARTIFACT, "prompt", payload, null));
        List<Ref> withNull = new java.util.ArrayList<>();
        withNull.add(null);
        assertThrows(IllegalArgumentException.class,
                () -> Revision.of(Kind.ARTIFACT, "prompt", payload, withNull));
    }
}
