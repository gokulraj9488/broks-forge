package com.broksforge.explorer.render;

import com.broksforge.kernel.api.canonical.CanonicalValue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Small read-side helpers over {@link CanonicalValue}.
 *
 * <p>The kernel's canonical model is a clean sealed hierarchy, which is pleasant to build with
 * (via {@code CanonicalValue.objectBuilder()}) but deliberately gives no typed accessors for reading
 * back — an application that wants "the {@code text} field of this payload as a String" must pattern
 * match the sealed variants itself. These helpers are that read-side ergonomics, kept in one place.
 */
public final class Values {

    private Values() {
    }

    /**
     * @param value a value expected to be an object
     * @param key   the field name
     * @return the field's string value, if present and a string
     */
    public static Optional<String> string(CanonicalValue value, String key) {
        return field(value, key).flatMap(v -> v instanceof CanonicalValue.Str s
                ? Optional.of(s.value()) : Optional.empty());
    }

    /**
     * @param value a value expected to be an object
     * @param key   the field name
     * @return the field's numeric value, if present and numeric
     */
    public static Optional<BigDecimal> number(CanonicalValue value, String key) {
        return field(value, key).flatMap(v -> v instanceof CanonicalValue.Num n
                ? Optional.of(n.value()) : Optional.empty());
    }

    /**
     * @param value a value expected to be an object
     * @param key   the field name
     * @return the field, if the value is an object that has it
     */
    public static Optional<CanonicalValue> field(CanonicalValue value, String key) {
        if (value instanceof CanonicalValue.Obj o) {
            return Optional.ofNullable(o.entries().get(key));
        }
        return Optional.empty();
    }

    /**
     * A compact one-line rendering of a value, suitable for tables and logs.
     *
     * @param value the value
     * @return a short human-readable string
     */
    public static String oneLine(CanonicalValue value) {
        return switch (value) {
            case CanonicalValue.Null ignored -> "∅";
            case CanonicalValue.Bool b -> Boolean.toString(b.value());
            case CanonicalValue.Str s -> '"' + ellipsize(s.value(), 60) + '"';
            case CanonicalValue.Num n -> n.value().toPlainString();
            case CanonicalValue.Arr a -> a.items().stream().map(Values::oneLine)
                    .collect(Collectors.joining(", ", "[", "]"));
            case CanonicalValue.Obj o -> o.entries().entrySet().stream()
                    .map(e -> e.getKey() + "=" + oneLine(e.getValue()))
                    .collect(Collectors.joining(", ", "{", "}"));
        };
    }

    /**
     * A multi-line, indented rendering of a value for detailed inspection.
     *
     * @param value the value
     * @return an indented pretty form
     */
    public static String pretty(CanonicalValue value) {
        StringBuilder sb = new StringBuilder();
        pretty(value, 0, sb);
        return sb.toString();
    }

    private static void pretty(CanonicalValue value, int indent, StringBuilder sb) {
        switch (value) {
            case CanonicalValue.Obj o -> {
                sb.append("{\n");
                for (Map.Entry<String, CanonicalValue> e : o.entries().entrySet()) {
                    pad(sb, indent + 2).append(e.getKey()).append(": ");
                    pretty(e.getValue(), indent + 2, sb);
                    sb.append('\n');
                }
                pad(sb, indent).append('}');
            }
            case CanonicalValue.Arr a -> {
                List<CanonicalValue> items = a.items();
                if (items.isEmpty()) {
                    sb.append("[]");
                    return;
                }
                sb.append("[\n");
                for (CanonicalValue item : items) {
                    pad(sb, indent + 2);
                    pretty(item, indent + 2, sb);
                    sb.append('\n');
                }
                pad(sb, indent).append(']');
            }
            default -> sb.append(oneLine(value));
        }
    }

    private static StringBuilder pad(StringBuilder sb, int n) {
        return sb.append(" ".repeat(n));
    }

    private static String ellipsize(String s, int max) {
        String flat = s.replace("\n", "\\n");
        return flat.length() <= max ? flat : flat.substring(0, max - 1) + "…";
    }
}
