package com.broksforge.kernel.api.canonical;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Property-based tests for the canonical serializer, implemented on JUnit with a seeded PRNG (no
 * extra test framework — the kernel keeps its dependency surface minimal). Each property runs over
 * many randomly generated value trees; the seed is fixed so failures are reproducible.
 *
 * <p>Properties verified:
 * <ul>
 *   <li><b>Determinism</b> — the same value encodes to identical bytes every time.</li>
 *   <li><b>Key-order independence</b> — object key insertion order never affects the encoding.</li>
 *   <li><b>Number canonicality</b> — {@code 1}, {@code 1.0}, {@code 1.00} encode identically.</li>
 *   <li><b>NFC idempotence</b> — decomposed and composed forms of the same text encode alike.</li>
 * </ul>
 */
class CanonicalSerializerPropertyTest {

    private static final long SEED = 0x5DEECE66DL;
    private static final int ITERATIONS = 2_000;

    @Test
    @DisplayName("determinism: same value -> identical bytes, always")
    void determinism() {
        Random rnd = new Random(SEED);
        for (int i = 0; i < ITERATIONS; i++) {
            CanonicalValue v = randomValue(rnd, 4);
            assertArrayEquals(CanonicalSerializer.toBytes(v), CanonicalSerializer.toBytes(v),
                    "encoding must be deterministic");
        }
    }

    @Test
    @DisplayName("key-order independence: shuffled object keys -> identical encoding")
    void keyOrderIndependence() {
        Random rnd = new Random(SEED + 1);
        for (int i = 0; i < ITERATIONS; i++) {
            List<String> keys = new ArrayList<>();
            Map<String, CanonicalValue> base = new LinkedHashMap<>();
            int n = 1 + rnd.nextInt(6);
            for (int k = 0; k < n; k++) {
                String key = randomKey(rnd);
                keys.add(key);
                base.put(key, randomScalar(rnd));
            }
            List<String> shuffled = new ArrayList<>(keys);
            Collections.shuffle(shuffled, rnd);
            Map<String, CanonicalValue> other = new LinkedHashMap<>();
            for (String key : shuffled) {
                other.put(key, base.get(key));
            }
            assertEquals(
                    CanonicalSerializer.toCanonicalString(CanonicalValue.object(base)),
                    CanonicalSerializer.toCanonicalString(CanonicalValue.object(other)),
                    "object encoding must not depend on key insertion order");
        }
    }

    @Test
    @DisplayName("number canonicality: scale does not change the encoding")
    void numberCanonicality() {
        assertEquals(
                CanonicalSerializer.toCanonicalString(CanonicalValue.of(new BigDecimal("1"))),
                CanonicalSerializer.toCanonicalString(CanonicalValue.of(new BigDecimal("1.0"))));
        assertEquals(
                CanonicalSerializer.toCanonicalString(CanonicalValue.of(new BigDecimal("1.0"))),
                CanonicalSerializer.toCanonicalString(CanonicalValue.of(new BigDecimal("1.00"))));

        Random rnd = new Random(SEED + 2);
        for (int i = 0; i < ITERATIONS; i++) {
            long value = rnd.nextLong() % 1_000_000L;
            BigDecimal a = BigDecimal.valueOf(value);
            BigDecimal b = a.setScale(1 + rnd.nextInt(5)); // same value, extra trailing zeros
            assertEquals(
                    CanonicalSerializer.toCanonicalString(CanonicalValue.of(a)),
                    CanonicalSerializer.toCanonicalString(CanonicalValue.of(b)),
                    "trailing zeros must not change the encoding of " + a);
        }
    }

    @Test
    @DisplayName("NFC idempotence: decomposed and composed text encode identically")
    void nfcIdempotence() {
        // Decomposed (NFD): 'e' (U+0065) + combining acute accent (U+0301).
        String decomposed = "café";
        // Composed (NFC): precomposed e-acute (U+00E9).
        String composed = "café";
        // Sanity: the two are genuinely different Java strings before normalization.
        assertEquals(5, decomposed.length());
        assertEquals(4, composed.length());
        assertEquals(
                CanonicalSerializer.toCanonicalString(CanonicalValue.of(decomposed)),
                CanonicalSerializer.toCanonicalString(CanonicalValue.of(composed)),
                "NFC normalization must make the two forms encode identically");
    }

    // ---- generators --------------------------------------------------------------------------

    private static CanonicalValue randomValue(Random rnd, int depth) {
        if (depth <= 0 || rnd.nextInt(3) == 0) {
            return randomScalar(rnd);
        }
        if (rnd.nextBoolean()) {
            int n = rnd.nextInt(5);
            List<CanonicalValue> items = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                items.add(randomValue(rnd, depth - 1));
            }
            return CanonicalValue.array(items);
        }
        int n = rnd.nextInt(5);
        Map<String, CanonicalValue> entries = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            entries.put(randomKey(rnd), randomValue(rnd, depth - 1));
        }
        return CanonicalValue.object(entries);
    }

    private static CanonicalValue randomScalar(Random rnd) {
        return switch (rnd.nextInt(5)) {
            case 0 -> CanonicalValue.NULL;
            case 1 -> CanonicalValue.of(rnd.nextBoolean());
            case 2 -> CanonicalValue.of(rnd.nextLong() % 100_000L);
            case 3 -> CanonicalValue.of(new BigDecimal(rnd.nextLong() % 1000 + "." + rnd.nextInt(100)));
            default -> CanonicalValue.of(randomString(rnd));
        };
    }

    private static String randomKey(Random rnd) {
        char[] alphabet = "abcdefghij".toCharArray();
        int len = 1 + rnd.nextInt(6);
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(alphabet[rnd.nextInt(alphabet.length)]);
        }
        return sb.toString();
    }

    private static String randomString(Random rnd) {
        // Exercises escaping (quote, backslash, newline, tab) and a non-ASCII code point (U+00E9),
        // written as an escape to stay source-encoding independent.
        char[] alphabet = {'a', 'b', 'c', ' ', '"', '\\', '\n', '\t', 'é'};
        int len = rnd.nextInt(8);
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(alphabet[rnd.nextInt(alphabet.length)]);
        }
        return sb.toString();
    }
}
