package com.broksforge.kernel.api.canonical;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link CanonicalParser}: the round-trip contract with the serializer, the {@code Num}
 * equality/normalization fix, and defensive rejection of malformed input (serialization safety).
 */
class CanonicalParserTest {

    private static final long SEED = 0x1234_5678L;
    private static final int ITERATIONS = 3_000;

    @Test
    @DisplayName("round-trip: parse(toBytes(v)).equals(v) for random trees")
    void roundTrip() {
        Random rnd = new Random(SEED);
        for (int i = 0; i < ITERATIONS; i++) {
            CanonicalValue v = randomValue(rnd, 4);
            CanonicalValue back = CanonicalParser.parse(CanonicalSerializer.toBytes(v));
            assertEquals(v, back, "round-trip must preserve value");
            // And re-serialization is byte-identical (canonical stability).
            assertEquals(CanonicalSerializer.toCanonicalString(v), CanonicalSerializer.toCanonicalString(back));
        }
    }

    @Test
    @DisplayName("Num normalization makes scale-different decimals equal and hash-equal")
    void numNormalization() {
        assertEquals(CanonicalValue.of(new BigDecimal("1.0")), CanonicalValue.of(1));
        assertEquals(CanonicalValue.of(new BigDecimal("1.00")), CanonicalValue.of(new BigDecimal("1")));
        assertEquals(CanonicalValue.of(new BigDecimal("0.0")), CanonicalValue.of(0));
        assertEquals(ContentHash.of(CanonicalValue.of(new BigDecimal("600.0"))),
                ContentHash.of(CanonicalValue.of(new BigDecimal("600"))));
        assertEquals(CanonicalValue.of(new BigDecimal("1")), CanonicalParser.parse("1.00"));
    }

    @Test
    @DisplayName("parses the canonical forms the serializer emits")
    void parsesCanonicalForms() {
        assertEquals(CanonicalValue.NULL, CanonicalParser.parse("null"));
        assertEquals(CanonicalValue.of(true), CanonicalParser.parse("true"));
        assertEquals(CanonicalValue.of("a\nb"), CanonicalParser.parse("\"a\\nb\""));
        assertEquals(CanonicalValue.of(-5), CanonicalParser.parse("-5"));
        assertEquals(CanonicalValue.of(new BigDecimal("0.78")), CanonicalParser.parse("0.78"));
        assertEquals(3, ((CanonicalValue.Arr) CanonicalParser.parse("[1,2,3]")).items().size());
        Map<String, CanonicalValue> obj = ((CanonicalValue.Obj) CanonicalParser.parse("{\"a\":2,\"b\":1}")).entries();
        assertEquals(2, obj.size());
    }

    @Test
    @DisplayName("rejects malformed input cleanly")
    void rejectsMalformed() {
        for (String bad : List.of("", "{", "[1,", "\"unterminated", "1.2.3", "1e5", "nul",
                "{\"a\"}", "1 2", "[1 2]", "{\"a\":1,}", "tru")) {
            assertThrows(IllegalArgumentException.class, () -> CanonicalParser.parse(bad), "should reject: " + bad);
        }
    }

    @Test
    @DisplayName("fuzz: arbitrary input never crashes or hangs — parses or throws IllegalArgumentException")
    void fuzz() {
        Random rnd = new Random(SEED + 7);
        char[] alphabet = {'{', '}', '[', ']', '"', ':', ',', '\\', 'n', 't', 'u', '0', '1', '.', '-', ' '};
        for (int i = 0; i < 5_000; i++) {
            int len = rnd.nextInt(24);
            StringBuilder sb = new StringBuilder(len);
            for (int j = 0; j < len; j++) {
                sb.append(alphabet[rnd.nextInt(alphabet.length)]);
            }
            String input = sb.toString();
            try {
                CanonicalParser.parse(input);
            } catch (IllegalArgumentException expected) {
                // acceptable: malformed input is rejected cleanly
            } catch (Throwable unexpected) {
                throw new AssertionError("parser threw " + unexpected.getClass() + " on: " + input, unexpected);
            }
        }
    }

    // ---- generator (mirrors the serializer property test) ------------------------------------

    private static CanonicalValue randomValue(Random rnd, int depth) {
        if (depth <= 0 || rnd.nextInt(3) == 0) {
            return randomScalar(rnd);
        }
        if (rnd.nextBoolean()) {
            int n = rnd.nextInt(5);
            List<CanonicalValue> items = new ArrayList<>();
            for (int k = 0; k < n; k++) {
                items.add(randomValue(rnd, depth - 1));
            }
            return CanonicalValue.array(items);
        }
        int n = rnd.nextInt(5);
        Map<String, CanonicalValue> entries = new LinkedHashMap<>();
        for (int k = 0; k < n; k++) {
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
        for (int k = 0; k < len; k++) {
            sb.append(alphabet[rnd.nextInt(alphabet.length)]);
        }
        return sb.toString();
    }

    private static String randomString(Random rnd) {
        char[] alphabet = {'a', 'b', 'c', ' ', '"', '\\', '\n', '\t', 'é'};
        int len = rnd.nextInt(8);
        StringBuilder sb = new StringBuilder(len);
        for (int k = 0; k < len; k++) {
            sb.append(alphabet[rnd.nextInt(alphabet.length)]);
        }
        return sb.toString();
    }
}
