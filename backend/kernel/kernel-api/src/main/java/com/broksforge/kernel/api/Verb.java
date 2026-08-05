package com.broksforge.kernel.api;

import java.util.regex.Pattern;

/**
 * A relationship verb, classified into exactly one {@link EdgeFamily}.
 *
 * <p>Verbs are the open half of the "closed families, open verbs" rule (MANIFESTO Article III):
 * new verbs are registered data, not code changes, so this type does <em>not</em> enumerate a
 * fixed list. It carries a verb name together with the family it belongs to, and validates the
 * name's shape. Each {@code Verb} therefore belongs to exactly one family — satisfying Article III
 * per assertion — but the kernel deliberately keeps <em>no</em> global name&rarr;family registry:
 * doing so would introduce mutable state outside the log (against the substrate model) and narrow the
 * "open verbs" guarantee. Which family a verb name <em>conventionally</em> belongs to is therefore a
 * userspace concern; applications that want one name to mean one family everywhere should keep a
 * shared verb catalog (see the Kernel Amendment Review, KAP-2). This value type simply records the
 * pairing immutably.
 *
 * <p>Example verbs by family (illustrative, not exhaustive): {@code uses}, {@code depends_on}
 * (composition); {@code derived_from}, {@code supersedes} (derivation); {@code cites},
 * {@code refutes} (evidence); {@code caused}, {@code triggered} (causality); {@code decided_by},
 * {@code applied} (intent).
 *
 * @param name   the verb name: lower snake_case, starting with a letter
 * @param family the family this verb belongs to
 */
public record Verb(String name, EdgeFamily family) {

    /** Verb names are lower snake_case, must start with a letter, 1..64 chars. */
    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_]{0,63}");

    /**
     * Validates the verb name and family.
     *
     * @throws IllegalArgumentException if the name is null, malformed, or the family is null
     */
    public Verb {
        if (name == null) {
            throw new IllegalArgumentException("verb name must not be null");
        }
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "verb name must be lower snake_case starting with a letter (1..64 chars): " + name);
        }
        if (family == null) {
            throw new IllegalArgumentException("verb family must not be null");
        }
    }
}
