package com.broksforge.kernel.api;

/**
 * The identity of an actor — the signer of every append (Law 2, total provenance).
 *
 * <p>An actor is whoever performs an append: a human engineer, an external system (CI), or one of
 * the kernel's own programs. Every fact in the graph carries an {@code ActorId}; there is no
 * anonymous write and no privileged writer (Law 9) — the kernel's programs sign their appends with
 * an {@code ActorId} exactly as a human does. This value object is only the identity <em>token</em>;
 * authenticating that a caller is entitled to use a given token is a higher layer's concern.
 *
 * <p>The token is a free-form but disciplined string (for example a UUID for a human,
 * {@code system:ci} for an automation, {@code program:nightly-pass} for a kernel program): non-blank,
 * at most 256 characters, with no whitespace or control characters so it is safe in logs and URIs.
 *
 * @param value the identity token
 */
public record ActorId(String value) {

    private static final int MAX_LENGTH = 256;

    /**
     * @throws IllegalArgumentException if the token is null, blank, too long, or contains
     *                                  whitespace or control characters
     */
    public ActorId {
        if (value == null) {
            throw new IllegalArgumentException("actor id must not be null");
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException("actor id must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("actor id too long (max " + MAX_LENGTH + ")");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isWhitespace(c) || Character.isISOControl(c)) {
                throw new IllegalArgumentException("actor id must not contain whitespace or control characters");
            }
        }
    }

    /**
     * @param value the identity token
     * @return the actor id
     */
    public static ActorId of(String value) {
        return new ActorId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
