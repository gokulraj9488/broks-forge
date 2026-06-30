package com.broksforge.common.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal, dependency-free RFC&nbsp;4180 CSV reader and a hardened writer.
 *
 * <p>The reader supports quoted fields, escaped quotes ({@code ""}), and commas /
 * newlines inside quotes. The writer additionally neutralises
 * <em>CSV/formula injection</em>: any field beginning with {@code = + - @} or a
 * control character is prefixed with a single quote so spreadsheet software does
 * not execute it. This matters because dataset content is user-supplied and later
 * exported in reports (see SECURITY_GUIDE.md).</p>
 */
public final class Csv {

    private static final char DELIMITER = ',';
    private static final char QUOTE = '"';

    private Csv() {
    }

    /**
     * Parses CSV {@code content} into rows of string fields. Blank trailing lines
     * are ignored. Carriage returns are tolerated (CRLF or LF line endings).
     */
    public static List<List<String>> parse(String content) {
        List<List<String>> rows = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return rows;
        }
        List<String> current = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        int i = 0;
        int n = content.length();
        while (i < n) {
            char c = content.charAt(i);
            if (inQuotes) {
                if (c == QUOTE) {
                    if (i + 1 < n && content.charAt(i + 1) == QUOTE) {
                        field.append(QUOTE);
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else {
                switch (c) {
                    case QUOTE -> inQuotes = true;
                    case DELIMITER -> {
                        current.add(field.toString());
                        field.setLength(0);
                    }
                    case '\r' -> { /* swallow; handled by following \n or EOL */ }
                    case '\n' -> {
                        current.add(field.toString());
                        field.setLength(0);
                        rows.add(current);
                        current = new ArrayList<>();
                    }
                    default -> field.append(c);
                }
            }
            i++;
        }
        // Flush the final field/row if the content did not end with a newline.
        if (field.length() > 0 || !current.isEmpty()) {
            current.add(field.toString());
            rows.add(current);
        }
        return stripTrailingBlankRows(rows);
    }

    /**
     * Serialises {@code rows} into an RFC&nbsp;4180 document with CRLF line endings
     * and formula-injection neutralisation.
     */
    public static String write(List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        for (List<String> row : rows) {
            for (int c = 0; c < row.size(); c++) {
                if (c > 0) {
                    sb.append(DELIMITER);
                }
                sb.append(escapeField(row.get(c)));
            }
            sb.append("\r\n");
        }
        return sb.toString();
    }

    /**
     * Escapes a single field for safe CSV export: neutralises leading formula
     * triggers and quotes the value when it contains a delimiter, quote or newline.
     */
    public static String escapeField(String value) {
        String v = value == null ? "" : value;
        if (!v.isEmpty()) {
            char first = v.charAt(0);
            if (first == '=' || first == '+' || first == '-' || first == '@'
                    || first == '\t' || first == '\r') {
                v = "'" + v;
            }
        }
        boolean mustQuote = v.indexOf(DELIMITER) >= 0 || v.indexOf(QUOTE) >= 0
                || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0;
        if (!mustQuote) {
            return v;
        }
        return QUOTE + v.replace("\"", "\"\"") + QUOTE;
    }

    private static List<List<String>> stripTrailingBlankRows(List<List<String>> rows) {
        int last = rows.size();
        while (last > 0 && isBlankRow(rows.get(last - 1))) {
            last--;
        }
        return new ArrayList<>(rows.subList(0, last));
    }

    private static boolean isBlankRow(List<String> row) {
        return row.stream().allMatch(s -> s == null || s.isBlank());
    }
}
