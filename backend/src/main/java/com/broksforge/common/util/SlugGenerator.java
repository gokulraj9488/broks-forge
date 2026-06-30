package com.broksforge.common.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Generates URL-friendly slugs from arbitrary display names and guarantees
 * uniqueness via a caller-supplied existence check.
 */
public final class SlugGenerator {

    private static final Pattern NON_LATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]+");
    private static final Pattern MULTI_DASH = Pattern.compile("-{2,}");
    private static final Pattern EDGE_DASH = Pattern.compile("(^-+|-+$)");
    private static final int MAX_BASE_LENGTH = 48;

    private SlugGenerator() {
    }

    /**
     * Normalises {@code input} into a slug (lowercase, hyphen-separated, ASCII).
     */
    public static String slugify(String input) {
        if (input == null || input.isBlank()) {
            return "untitled";
        }
        String nowhitespace = WHITESPACE.matcher(input.trim()).replaceAll("-");
        String normalized = Normalizer.normalize(nowhitespace, Normalizer.Form.NFD);
        String slug = NON_LATIN.matcher(normalized).replaceAll("");
        slug = MULTI_DASH.matcher(slug).replaceAll("-");
        slug = EDGE_DASH.matcher(slug).replaceAll("");
        slug = slug.toLowerCase(Locale.ENGLISH);
        if (slug.length() > MAX_BASE_LENGTH) {
            slug = EDGE_DASH.matcher(slug.substring(0, MAX_BASE_LENGTH)).replaceAll("");
        }
        return slug.isBlank() ? "untitled" : slug;
    }

    /**
     * Produces a unique slug from {@code input}, appending {@code -2}, {@code -3}
     * etc. until {@code exists} returns false.
     *
     * @param input  the display name
     * @param exists predicate that returns {@code true} if a candidate slug is taken
     */
    public static String uniqueSlug(String input, Predicate<String> exists) {
        String base = slugify(input);
        if (!exists.test(base)) {
            return base;
        }
        for (int suffix = 2; suffix < Integer.MAX_VALUE; suffix++) {
            String candidate = base + "-" + suffix;
            if (!exists.test(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate a unique slug for: " + input);
    }
}
