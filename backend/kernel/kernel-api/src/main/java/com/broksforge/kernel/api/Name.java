package com.broksforge.kernel.api;

import java.util.List;
import java.util.regex.Pattern;

/**
 * A name — the only mutable concept in the Forge Graph.
 *
 * <p>A name is a path (for example {@code prod}, {@code agents/support/current},
 * {@code suites/nightly}) that points to a revision or a closure. The <em>pointer</em> changes
 * over time (via name-repoint appends), but this value object is only the immutable
 * <em>path</em>: deployment, rollback, promotion, and branching are all repointings of names
 * (ADR-V2-0006, docs/v2/DOMAIN_MODEL.md §7). Resolving a name is a kernel-core operation; this
 * type validates the path.
 *
 * <p>Path rules: one or more {@code /}-separated segments; each segment starts with a letter or
 * digit and then allows letters, digits, {@code . _ -}; no empty segments; no {@code .} or
 * {@code ..} segments; at most 32 segments; total length at most 512 characters. The segment
 * {@code name} is permitted here — the reservation of {@code name} as an address discriminator is
 * enforced by {@link Address}, not by this type.
 *
 * @param path the raw path string
 */
public record Name(String path) {

    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");
    private static final int MAX_SEGMENTS = 32;
    private static final int MAX_LENGTH = 512;

    /**
     * @throws IllegalArgumentException if the path is null, empty, too long, or has an illegal
     *                                  segment
     */
    public Name {
        if (path == null) {
            throw new IllegalArgumentException("name path must not be null");
        }
        if (path.isEmpty()) {
            throw new IllegalArgumentException("name path must not be empty");
        }
        if (path.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("name path too long (max " + MAX_LENGTH + "): " + path);
        }
        String[] segments = path.split("/", -1);
        if (segments.length > MAX_SEGMENTS) {
            throw new IllegalArgumentException("name has too many segments (max " + MAX_SEGMENTS + "): " + path);
        }
        for (String seg : segments) {
            if (seg.isEmpty()) {
                throw new IllegalArgumentException("name has an empty segment: " + path);
            }
            if (seg.equals(".") || seg.equals("..")) {
                throw new IllegalArgumentException("name segment must not be '.' or '..': " + path);
            }
            if (!SEGMENT.matcher(seg).matches()) {
                throw new IllegalArgumentException("illegal name segment '" + seg + "' in: " + path);
            }
        }
    }

    /**
     * Builds a name from a path string.
     *
     * @param path the path
     * @return the name
     */
    public static Name of(String path) {
        return new Name(path);
    }

    /**
     * @return the path segments, in order (never empty)
     */
    public List<String> segments() {
        return List.of(path.split("/", -1));
    }

    @Override
    public String toString() {
        return path;
    }
}
