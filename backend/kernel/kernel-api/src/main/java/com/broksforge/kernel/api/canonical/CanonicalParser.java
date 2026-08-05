package com.broksforge.kernel.api.canonical;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

/**
 * Parses canonical bytes back into a {@link CanonicalValue} — the inverse of
 * {@link CanonicalSerializer}, and the enabler of persistence: a stored revision is rebuilt by
 * parsing its canonical content.
 *
 * <p>The round-trip contract is {@code parse(toBytes(v)).equals(v)} for every value {@code v}
 * (guaranteed by {@code Num} normalization and {@code Str} NFC normalization). The parser is strict
 * and defensive: it accepts the canonical JSON subset the serializer emits (tolerating insignificant
 * whitespace for hand-authored input), and rejects anything else with an {@link IllegalArgumentException}
 * rather than crashing or looping — the index always advances, so termination is guaranteed on any
 * input (serialization safety).
 *
 * <p>Numbers are parsed as arbitrary-precision decimals; exponent notation is rejected, consistent
 * with the no-binary-floating-point rule of the canonical model.
 */
public final class CanonicalParser {

    private CanonicalParser() {
    }

    /**
     * @param bytes canonical UTF-8 bytes
     * @return the parsed value
     * @throws IllegalArgumentException if the input is malformed
     */
    public static CanonicalValue parse(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("bytes must not be null");
        }
        return parse(new String(bytes, StandardCharsets.UTF_8));
    }

    /**
     * @param text canonical text
     * @return the parsed value
     * @throws IllegalArgumentException if the input is malformed
     */
    public static CanonicalValue parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
        Cursor c = new Cursor(text);
        c.skipWhitespace();
        CanonicalValue value = c.readValue();
        c.skipWhitespace();
        if (!c.atEnd()) {
            throw c.error("trailing content after value");
        }
        return value;
    }

    private static final class Cursor {
        private final String s;
        private int i;

        Cursor(String s) {
            this.s = s;
        }

        boolean atEnd() {
            return i >= s.length();
        }

        private char peek() {
            if (atEnd()) {
                throw error("unexpected end of input");
            }
            return s.charAt(i);
        }

        void skipWhitespace() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    i++;
                } else {
                    break;
                }
            }
        }

        CanonicalValue readValue() {
            skipWhitespace();
            char c = peek();
            return switch (c) {
                case 'n' -> readLiteral("null", CanonicalValue.NULL);
                case 't' -> readLiteral("true", CanonicalValue.of(true));
                case 'f' -> readLiteral("false", CanonicalValue.of(false));
                case '"' -> CanonicalValue.of(readString());
                case '[' -> readArray();
                case '{' -> readObject();
                default -> {
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        yield readNumber();
                    }
                    throw error("unexpected character '" + c + "'");
                }
            };
        }

        private CanonicalValue readLiteral(String literal, CanonicalValue value) {
            if (!s.startsWith(literal, i)) {
                throw error("expected '" + literal + "'");
            }
            i += literal.length();
            return value;
        }

        private String readString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw error("unterminated string");
                }
                char c = s.charAt(i++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (atEnd()) {
                        throw error("unterminated escape");
                    }
                    char e = s.charAt(i++);
                    switch (e) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 't' -> sb.append('\t');
                        case 'n' -> sb.append('\n');
                        case 'f' -> sb.append('\f');
                        case 'r' -> sb.append('\r');
                        case 'u' -> sb.append(readUnicodeEscape());
                        default -> throw error("invalid escape '\\" + e + "'");
                    }
                } else if (c < 0x20) {
                    throw error("unescaped control character in string");
                } else {
                    sb.append(c);
                }
            }
        }

        private char readUnicodeEscape() {
            if (i + 4 > s.length()) {
                throw error("truncated unicode escape");
            }
            int code = 0;
            for (int k = 0; k < 4; k++) {
                char h = s.charAt(i++);
                int digit = Character.digit(h, 16);
                if (digit < 0) {
                    throw error("invalid unicode escape digit '" + h + "'");
                }
                code = (code << 4) | digit;
            }
            return (char) code;
        }

        private CanonicalValue readNumber() {
            int start = i;
            if (peek() == '-') {
                i++;
            }
            requireDigits();
            if (i < s.length() && s.charAt(i) == '.') {
                i++;
                requireDigits();
            }
            if (i < s.length() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
                throw error("exponent notation is not part of the canonical number model");
            }
            String number = s.substring(start, i);
            try {
                return CanonicalValue.of(new BigDecimal(number));
            } catch (NumberFormatException nfe) {
                throw error("invalid number '" + number + "'");
            }
        }

        private void requireDigits() {
            int start = i;
            while (i < s.length() && s.charAt(i) >= '0' && s.charAt(i) <= '9') {
                i++;
            }
            if (i == start) {
                throw error("expected digit");
            }
        }

        private CanonicalValue readArray() {
            expect('[');
            skipWhitespace();
            java.util.List<CanonicalValue> items = new java.util.ArrayList<>();
            if (peek() == ']') {
                i++;
                return CanonicalValue.array(items);
            }
            while (true) {
                items.add(readValue());
                skipWhitespace();
                char c = peek();
                if (c == ',') {
                    i++;
                } else if (c == ']') {
                    i++;
                    return CanonicalValue.array(items);
                } else {
                    throw error("expected ',' or ']' in array");
                }
            }
        }

        private CanonicalValue readObject() {
            expect('{');
            skipWhitespace();
            CanonicalValue.ObjBuilder builder = CanonicalValue.objectBuilder();
            if (peek() == '}') {
                i++;
                return builder.build();
            }
            while (true) {
                skipWhitespace();
                String key = readString();
                skipWhitespace();
                expect(':');
                builder.put(key, readValue());
                skipWhitespace();
                char c = peek();
                if (c == ',') {
                    i++;
                } else if (c == '}') {
                    i++;
                    return builder.build();
                } else {
                    throw error("expected ',' or '}' in object");
                }
            }
        }

        private void expect(char c) {
            if (atEnd() || s.charAt(i) != c) {
                throw error("expected '" + c + "'");
            }
            i++;
        }

        IllegalArgumentException error(String message) {
            return new IllegalArgumentException("canonical parse error at " + i + ": " + message);
        }
    }
}
