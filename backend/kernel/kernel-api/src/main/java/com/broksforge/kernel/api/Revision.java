package com.broksforge.kernel.api;

import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.kernel.api.canonical.ContentHash;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * An immutable node revision — one content-addressed state of a continuant.
 *
 * <p>A revision is a self-contained document: its {@link Kind}, an open {@code subtype}, a
 * {@link CanonicalValue} payload, and its intrinsic {@link Ref}s (the hash-pinned references that
 * define what it is). Its identity is the {@link RevisionHash} of its canonical form — so equal
 * content is the same revision (Law 3, deduplication) and references form a Merkle DAG.
 *
 * <p><b>What a revision does not contain:</b> the {@link NodeId} of the continuant it belongs to,
 * the actor, and the times. Those live on the <em>fact</em> that asserts the revision, not in the
 * content hash — which is why two actors asserting identical content share one revision but produce
 * two facts (DOMAIN_MODEL §6). This keeps the hash a pure function of content.
 *
 * <p><b>Reference order is significant:</b> refs are encoded in the order given, so a component
 * whose order matters (an ordered tool list) is captured faithfully; reordering yields a different
 * revision. This mirrors array ordering in the canonical model.
 *
 * <p>The four kinds' laws (for example a claim's mandatory evidence/method/confidence) are enforced
 * by the append engine's kind validators ({@code com.broksforge.kernel.core.node.KindLaws}), not here:
 * this type is the structural value; the engine is where Law 5 and Law 6 become
 * unappendable-if-violated (see ADR-V2-0003, ADR-V2-0004).
 *
 * @param kind    the kernel kind; never null
 * @param subtype the open subtype token (lower kebab/snake, starts with a letter, 1..64 chars)
 * @param payload the content payload; never null (use {@link CanonicalValue#NULL} for none)
 * @param refs    the intrinsic references, in significant order; never null, no null elements
 */
public record Revision(Kind kind, String subtype, CanonicalValue payload, List<Ref> refs) {

    private static final Pattern SUBTYPE = Pattern.compile("[a-z][a-z0-9._-]{0,63}");

    /**
     * @throws IllegalArgumentException if kind/payload is null, the subtype is malformed, or any
     *                                  reference is null
     */
    public Revision {
        if (kind == null) {
            throw new IllegalArgumentException("revision kind must not be null");
        }
        if (subtype == null || !SUBTYPE.matcher(subtype).matches()) {
            throw new IllegalArgumentException(
                    "revision subtype must match " + SUBTYPE.pattern() + ": " + subtype);
        }
        if (payload == null) {
            throw new IllegalArgumentException("revision payload must not be null; use CanonicalValue.NULL");
        }
        if (refs == null) {
            throw new IllegalArgumentException("revision refs must not be null");
        }
        List<Ref> copy = new ArrayList<>(refs.size());
        for (Ref r : refs) {
            if (r == null) {
                throw new IllegalArgumentException("revision ref must not be null");
            }
            copy.add(r);
        }
        refs = List.copyOf(copy);
    }

    /**
     * @param kind    the kind
     * @param subtype the subtype token
     * @param payload the payload
     * @param refs    the intrinsic references
     * @return the revision
     */
    public static Revision of(Kind kind, String subtype, CanonicalValue payload, List<Ref> refs) {
        return new Revision(kind, subtype, payload, refs);
    }

    /**
     * @param kind    the kind
     * @param subtype the subtype token
     * @param payload the payload
     * @return a revision with no intrinsic references
     */
    public static Revision leaf(Kind kind, String subtype, CanonicalValue payload) {
        return new Revision(kind, subtype, payload, List.of());
    }

    /**
     * The canonical content document of this revision. This exact structure is what gets hashed;
     * it is stable and part of the content-addressing contract.
     *
     * @return the canonical form
     */
    public CanonicalValue canonicalForm() {
        List<CanonicalValue> refItems = new ArrayList<>(refs.size());
        for (Ref r : refs) {
            refItems.add(CanonicalValue.objectBuilder()
                    .put("verb", r.verb().name())
                    .put("family", r.verb().family().wireName())
                    .put("target", r.target().toString())
                    .build());
        }
        return CanonicalValue.objectBuilder()
                .put("kind", kind.wireName())
                .put("subtype", subtype)
                .put("payload", payload)
                .put("refs", CanonicalValue.array(refItems))
                .build();
    }

    /**
     * @return the content-addressed identity of this revision (Law 3)
     */
    public RevisionHash hash() {
        return ContentHash.of(canonicalForm());
    }
}
