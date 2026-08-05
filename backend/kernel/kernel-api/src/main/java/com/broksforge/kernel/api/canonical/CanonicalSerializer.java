package com.broksforge.kernel.api.canonical;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Encodes a {@link CanonicalValue} to its single deterministic byte representation.
 *
 * <p>This is a profile of RFC 8785 (JSON Canonicalization Scheme):
 * <ul>
 *   <li>Object keys are sorted by UTF-16 code unit ({@link String#compareTo}).</li>
 *   <li>No insignificant whitespace.</li>
 *   <li>Strings are already NFC-normalized (by {@link CanonicalValue.Str}) and are escaped per
 *       RFC 8785: only {@code "}, {@code \\}, and control characters {@code U+0000..U+001F} are
 *       escaped; everything else (including all non-ASCII) is emitted as raw UTF-8.</li>
 *   <li>Numbers use a strict canonical decimal form (see {@link #number(BigDecimal)}); binary
 *       floating point is not part of the model.</li>
 * </ul>
 *
 * <p>The result is UTF-8 bytes. The class is stateless and final; all methods are pure.
 */
public final class CanonicalSerializer {

    /** Lower-case, no delimiters — used only for the {@code \\u00xx} control-char escapes. */
    private static final HexFormat HEX = HexFormat.of();

    private CanonicalSerializer() {
    }

    /**
     * @param value the value tree
     * @return its canonical UTF-8 byte encoding
     */
    public static byte[] toBytes(CanonicalValue value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        StringBuilder sb = new StringBuilder(64);
        write(value, sb);
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * @param value the value tree
     * @return its canonical encoding as a {@link String} (the pre-UTF-8 form)
     */
    public static String toCanonicalString(CanonicalValue value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        StringBuilder sb = new StringBuilder(64);
        write(value, sb);
        return sb.toString();
    }

    private static void write(CanonicalValue value, StringBuilder sb) {
        switch (value) {
            case CanonicalValue.Null ignored -> sb.append("null");
            case CanonicalValue.Bool b -> sb.append(b.value() ? "true" : "false");
            case CanonicalValue.Str s -> string(s.value(), sb);
            case CanonicalValue.Num n -> sb.append(number(n.value()));
            case CanonicalValue.Arr a -> array(a.items(), sb);
            case CanonicalValue.Obj o -> object(o.entries(), sb);
        }
    }

    private static void array(List<CanonicalValue> items, StringBuilder sb) {
        sb.append('[');
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            write(items.get(i), sb);
        }
        sb.append(']');
    }

    private static void object(Map<String, CanonicalValue> entries, StringBuilder sb) {
        List<String> keys = new ArrayList<>(entries.keySet());
        // RFC 8785: sort by UTF-16 code units, which is exactly String natural ordering in Java.
        Collections.sort(keys);
        sb.append('{');
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            String key = keys.get(i);
            string(key, sb);
            sb.append(':');
            write(entries.get(key), sb);
        }
        sb.append('}');
    }

    /**
     * Canonical decimal form of a number.
     *
     * <p>Zero (of any scale) is {@code "0"}. Otherwise trailing zeros are stripped and the value
     * is written in plain notation (never scientific), so {@code 1}, {@code 1.0}, {@code 1.00} all
     * become {@code "1"}, {@code 0.780} becomes {@code "0.78"}, and {@code 600} stays {@code "600"}.
     *
     * @param value the decimal
     * @return its canonical textual form
     */
    static String number(BigDecimal value) {
        if (value.signum() == 0) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    /**
     * RFC 8785 string escaping. The input is already NFC-normalized.
     */
    private static void string(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\t' -> sb.append("\\t");
                case '\n' -> sb.append("\\n");
                case '\f' -> sb.append("\\f");
                case '\r' -> sb.append("\\r");
                default -> {
                    if (c < 0x20) {
                        sb.append("\\u").append(HEX.toHexDigits(c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
