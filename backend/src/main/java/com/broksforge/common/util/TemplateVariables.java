package com.broksforge.common.util;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mustache-style {@code {{ variable }}} placeholder handling for prompt templates.
 *
 * <p>Templates are treated strictly as <em>data</em>: variables are extracted by a
 * regex and substituted by literal string replacement. Template text is never
 * compiled or evaluated as code, which keeps prompt rendering injection-safe
 * (see ADR 0008).</p>
 */
public final class TemplateVariables {

    /** Matches {@code {{ name }}} where name is letters, digits, underscore, dot or hyphen. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.\\-]+)\\s*}}");

    private TemplateVariables() {
    }

    /**
     * Returns the distinct variable names referenced by {@code template}, in first
     * appearance order. An empty set is returned for {@code null}/blank input.
     */
    public static Set<String> extract(String template) {
        Set<String> names = new LinkedHashSet<>();
        if (template == null || template.isBlank()) {
            return names;
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    /**
     * Renders {@code template} by replacing every {@code {{name}}} with the matching
     * value from {@code values}. Unknown variables render as an empty string so a
     * partial value map never produces a broken prompt.
     *
     * @param template the template text (may be {@code null} → empty result)
     * @param values   variable values; {@code null} is treated as no values
     */
    public static String render(String template, Map<String, ?> values) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            Object value = values == null ? null : values.get(name);
            matcher.appendReplacement(out, Matcher.quoteReplacement(value == null ? "" : String.valueOf(value)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * Returns the variable names referenced by {@code template} that are absent from
     * {@code values} — used to validate that a render has every value it needs.
     */
    public static Set<String> missingVariables(String template, Map<String, ?> values) {
        Set<String> missing = new LinkedHashSet<>();
        for (String name : extract(template)) {
            if (values == null || !values.containsKey(name)) {
                missing.add(name);
            }
        }
        return missing;
    }
}
