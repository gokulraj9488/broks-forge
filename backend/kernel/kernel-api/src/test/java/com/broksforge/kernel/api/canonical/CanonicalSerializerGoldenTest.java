package com.broksforge.kernel.api.canonical;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Golden serialization vectors — the frozen, hand-verifiable contract of the canonical encoder.
 *
 * <p>These exact strings are the determinism guarantee of content addressing (Law 3). If any of
 * them ever changes, every previously computed {@link com.broksforge.kernel.api.RevisionHash} would
 * change, so this file is effectively immutable: a change here is a hash-algorithm migration, never
 * an edit.
 *
 * <p>Note on source style: control characters are produced via {@code (char)} casts and the
 * escaped-{@code u} expectations are built by concatenation, so that no {@code \\u} sequence ever
 * appears in a single string literal (which the Java source scanner would try to read as a unicode
 * escape).
 */
class CanonicalSerializerGoldenTest {

    /** The precomposed e-acute (U+00E9); written as an escape, which is a valid unicode escape. */
    private static final String E_ACUTE = "é";

    private static String enc(CanonicalValue v) {
        return CanonicalSerializer.toCanonicalString(v);
    }

    @Test
    @DisplayName("primitives")
    void primitives() {
        assertEquals("null", enc(CanonicalValue.NULL));
        assertEquals("true", enc(CanonicalValue.of(true)));
        assertEquals("false", enc(CanonicalValue.of(false)));
        assertEquals("\"\"", enc(CanonicalValue.of("")));
        assertEquals("\"hello\"", enc(CanonicalValue.of("hello")));
    }

    @Test
    @DisplayName("string escaping per RFC 8785")
    void stringEscaping() {
        assertEquals("\"a\\\"b\"", enc(CanonicalValue.of("a\"b")));
        assertEquals("\"a\\\\b\"", enc(CanonicalValue.of("a\\b")));
        assertEquals("\"line1\\nline2\"", enc(CanonicalValue.of("line1\nline2")));
        assertEquals("\"tab\\there\"", enc(CanonicalValue.of("tab\there")));
        assertEquals("\"\\r\\n\\b\\f\"", enc(CanonicalValue.of("\r\n\b\f")));
        // Control chars without a short escape become a lower-case backslash-u escape.
        assertEquals("\"\\" + "u0000\"", enc(CanonicalValue.of(String.valueOf((char) 0x00))));
        assertEquals("\"\\" + "u001f\"", enc(CanonicalValue.of(String.valueOf((char) 0x1f))));
    }

    @Test
    @DisplayName("non-ASCII is emitted raw (NFC), not escaped")
    void nonAsciiRaw() {
        assertEquals("\"caf" + E_ACUTE + "\"", enc(CanonicalValue.of("caf" + E_ACUTE)));
    }

    @Test
    @DisplayName("numbers use canonical decimal form")
    void numbers() {
        assertEquals("0", enc(CanonicalValue.of(0)));
        assertEquals("1", enc(CanonicalValue.of(1)));
        assertEquals("-5", enc(CanonicalValue.of(-5)));
        assertEquals("0.78", enc(CanonicalValue.of(new BigDecimal("0.78"))));
        assertEquals("1", enc(CanonicalValue.of(new BigDecimal("1.0"))));
        assertEquals("1", enc(CanonicalValue.of(new BigDecimal("1.00"))));
        assertEquals("100", enc(CanonicalValue.of(new BigDecimal("100.00"))));
        assertEquals("600", enc(CanonicalValue.of(new BigDecimal("600"))));
        assertEquals("0", enc(CanonicalValue.of(new BigDecimal("0.000"))));
        assertEquals("0.78", enc(CanonicalValue.of(new BigDecimal("0.780"))));
        assertEquals("-0.5", enc(CanonicalValue.of(new BigDecimal("-0.50"))));
    }

    @Test
    @DisplayName("arrays preserve order")
    void arrays() {
        assertEquals("[]", enc(CanonicalValue.array()));
        assertEquals("[1,2,3]",
                enc(CanonicalValue.array(CanonicalValue.of(1), CanonicalValue.of(2), CanonicalValue.of(3))));
        assertEquals("[3,1,2]",
                enc(CanonicalValue.array(CanonicalValue.of(3), CanonicalValue.of(1), CanonicalValue.of(2))));
    }

    @Test
    @DisplayName("object keys are sorted; insertion order is irrelevant")
    void objects() {
        assertEquals("{}", enc(CanonicalValue.objectBuilder().build()));

        CanonicalValue insertionOrderBA = CanonicalValue.objectBuilder()
                .put("b", 1)
                .put("a", 2)
                .build();
        assertEquals("{\"a\":2,\"b\":1}", enc(insertionOrderBA));
    }

    @Test
    @DisplayName("nested structure")
    void nested() {
        CanonicalValue v = CanonicalValue.objectBuilder()
                .put("z", CanonicalValue.array(
                        CanonicalValue.objectBuilder().put("k", true).build()))
                .put("a", CanonicalValue.NULL)
                .build();
        assertEquals("{\"a\":null,\"z\":[{\"k\":true}]}", enc(v));
    }

    @Test
    @DisplayName("byte encoding is UTF-8 of the canonical string")
    void bytesAreUtf8() {
        CanonicalValue v = CanonicalValue.of("caf" + E_ACUTE);
        byte[] bytes = CanonicalSerializer.toBytes(v);
        // "caf" + 0xC3 0xA9 (UTF-8 for e-acute) wrapped in quotes -> 7 bytes.
        assertEquals(7, bytes.length);
        assertEquals((byte) 0x22, bytes[0]);
        assertEquals((byte) 0xC3, bytes[4]);
        assertEquals((byte) 0xA9, bytes[5]);
        assertEquals((byte) 0x22, bytes[6]);
    }

    @Test
    @DisplayName("two objects with same entries, different insertion order, encode identically")
    void keyOrderIndependence() {
        Map<String, CanonicalValue> m1 = new LinkedHashMap<>();
        m1.put("alpha", CanonicalValue.of(1));
        m1.put("beta", CanonicalValue.of(2));
        m1.put("gamma", CanonicalValue.of(3));

        Map<String, CanonicalValue> m2 = new LinkedHashMap<>();
        m2.put("gamma", CanonicalValue.of(3));
        m2.put("alpha", CanonicalValue.of(1));
        m2.put("beta", CanonicalValue.of(2));

        assertEquals(enc(CanonicalValue.object(m1)), enc(CanonicalValue.object(m2)));
    }
}
