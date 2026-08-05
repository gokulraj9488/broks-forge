package com.broksforge.kernel.api.canonical;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The canonical content data model — a small, explicit, JSON-like value tree.
 *
 * <p>Everything the kernel content-addresses is expressed as a {@code CanonicalValue}, which
 * {@link CanonicalSerializer} encodes to a single deterministic byte string. The model is a sealed
 * hierarchy of six variants so it can be matched exhaustively and can never grow an ambiguous
 * case:
 *
 * <ul>
 *   <li>{@link Null} — the absent value.</li>
 *   <li>{@link Bool} — a boolean.</li>
 *   <li>{@link Str} — a string, normalized to Unicode NFC at construction.</li>
 *   <li>{@link Num} — an arbitrary-precision decimal. Binary floating point is not accepted.</li>
 *   <li>{@link Arr} — an ordered list (order is significant).</li>
 *   <li>{@link Obj} — a string-keyed map (key insertion order is <em>not</em> significant; keys
 *       are sorted at serialization).</li>
 * </ul>
 *
 * <p>All variants are immutable. Nested collections are defensively copied.
 */
public sealed interface CanonicalValue
        permits CanonicalValue.Null, CanonicalValue.Bool, CanonicalValue.Str,
                CanonicalValue.Num, CanonicalValue.Arr, CanonicalValue.Obj {

    /** The single canonical null value. */
    Null NULL = new Null();

    // ---- Factories ---------------------------------------------------------------------------

    /**
     * @param value the boolean
     * @return a boolean value
     */
    static Bool of(boolean value) {
        return value ? Bool.TRUE : Bool.FALSE;
    }

    /**
     * @param value the string; normalized to NFC
     * @return a string value
     */
    static Str of(String value) {
        return new Str(value);
    }

    /**
     * @param value the integer
     * @return a numeric value
     */
    static Num of(long value) {
        return new Num(BigDecimal.valueOf(value));
    }

    /**
     * @param value the integer
     * @return a numeric value
     */
    static Num of(BigInteger value) {
        return new Num(new BigDecimal(requireNonNullNumber(value)));
    }

    /**
     * @param value the decimal
     * @return a numeric value
     */
    static Num of(BigDecimal value) {
        return new Num(requireNonNullNumber(value));
    }

    /**
     * @param items the elements (order significant); none may be a Java {@code null}
     * @return an array value
     */
    static Arr array(List<CanonicalValue> items) {
        return new Arr(items);
    }

    /**
     * @param items the elements (order significant); none may be a Java {@code null}
     * @return an array value
     */
    static Arr array(CanonicalValue... items) {
        return new Arr(List.of(items));
    }

    /**
     * @param entries the key/value map; keys normalized to NFC, values non-null
     * @return an object value
     */
    static Obj object(Map<String, CanonicalValue> entries) {
        return new Obj(entries);
    }

    /** @return a fresh object builder */
    static ObjBuilder objectBuilder() {
        return new ObjBuilder();
    }

    private static <T> T requireNonNullNumber(T value) {
        if (value == null) {
            throw new IllegalArgumentException("numeric value must not be null");
        }
        return value;
    }

    // ---- Variants ----------------------------------------------------------------------------

    /** The absent value. */
    record Null() implements CanonicalValue {
    }

    /** A boolean. */
    record Bool(boolean value) implements CanonicalValue {
        /** Shared instance for {@code true}. */
        public static final Bool TRUE = new Bool(true);
        /** Shared instance for {@code false}. */
        public static final Bool FALSE = new Bool(false);
    }

    /** A string, normalized to Unicode NFC so equal text always encodes identically. */
    record Str(String value) implements CanonicalValue {
        /**
         * @throws IllegalArgumentException if {@code value} is null
         */
        public Str {
            if (value == null) {
                throw new IllegalArgumentException("string value must not be null");
            }
            value = Normalizer.normalize(value, Normalizer.Form.NFC);
        }
    }

    /**
     * An arbitrary-precision decimal number.
     *
     * <p>Binary floating point ({@code double}/{@code float}) is intentionally not accepted:
     * IEEE-754 formatting is a determinism hazard for content addressing. The stored
     * {@link BigDecimal} is serialized via a strict canonical textual form (see
     * {@link CanonicalSerializer}), so {@code 1}, {@code 1.0}, and {@code 1.00} encode identically.
     */
    record Num(BigDecimal value) implements CanonicalValue {
        /**
         * Normalizes the decimal to its canonical form at construction (zero as {@link BigDecimal#ZERO},
         * otherwise trailing zeros stripped). This makes value equality consistent with content
         * identity: {@code Num("1.0")}, {@code Num("1.00")}, and {@code Num("1")} are all equal and all
         * hash identically, exactly as the serializer already renders them.
         *
         * @throws IllegalArgumentException if {@code value} is null
         */
        public Num {
            if (value == null) {
                throw new IllegalArgumentException("numeric value must not be null");
            }
            value = value.signum() == 0 ? BigDecimal.ZERO : value.stripTrailingZeros();
        }
    }

    /** An ordered list; element order is significant. */
    record Arr(List<CanonicalValue> items) implements CanonicalValue {
        /**
         * @throws IllegalArgumentException if the list or any element is null
         */
        public Arr {
            if (items == null) {
                throw new IllegalArgumentException("array items must not be null");
            }
            List<CanonicalValue> copy = new ArrayList<>(items.size());
            for (CanonicalValue item : items) {
                if (item == null) {
                    throw new IllegalArgumentException("array element must not be null; use CanonicalValue.NULL");
                }
                copy.add(item);
            }
            items = List.copyOf(copy);
        }
    }

    /** A string-keyed map; key order is not significant (keys are sorted at serialization). */
    record Obj(Map<String, CanonicalValue> entries) implements CanonicalValue {
        /**
         * @throws IllegalArgumentException if the map, any key, or any value is null
         */
        public Obj {
            if (entries == null) {
                throw new IllegalArgumentException("object entries must not be null");
            }
            Map<String, CanonicalValue> copy = new LinkedHashMap<>();
            for (Map.Entry<String, CanonicalValue> e : entries.entrySet()) {
                if (e.getKey() == null) {
                    throw new IllegalArgumentException("object key must not be null");
                }
                if (e.getValue() == null) {
                    throw new IllegalArgumentException("object value must not be null; use CanonicalValue.NULL");
                }
                copy.put(Normalizer.normalize(e.getKey(), Normalizer.Form.NFC), e.getValue());
            }
            entries = Map.copyOf(copy);
        }
    }

    /** A small mutable builder for {@link Obj} values. */
    final class ObjBuilder {
        private final Map<String, CanonicalValue> entries = new LinkedHashMap<>();

        private ObjBuilder() {
        }

        /**
         * @param key   the key (normalized to NFC by {@link Obj})
         * @param value the value; not null
         * @return this builder
         */
        public ObjBuilder put(String key, CanonicalValue value) {
            if (key == null) {
                throw new IllegalArgumentException("object key must not be null");
            }
            if (value == null) {
                throw new IllegalArgumentException("object value must not be null; use CanonicalValue.NULL");
            }
            entries.put(key, value);
            return this;
        }

        /**
         * @param key   the key
         * @param value the string value
         * @return this builder
         */
        public ObjBuilder put(String key, String value) {
            return put(key, CanonicalValue.of(value));
        }

        /**
         * @param key   the key
         * @param value the integer value
         * @return this builder
         */
        public ObjBuilder put(String key, long value) {
            return put(key, CanonicalValue.of(value));
        }

        /**
         * @param key   the key
         * @param value the boolean value
         * @return this builder
         */
        public ObjBuilder put(String key, boolean value) {
            return put(key, CanonicalValue.of(value));
        }

        /** @return the built object */
        public Obj build() {
            return new Obj(entries);
        }
    }
}
