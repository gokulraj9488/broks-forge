package com.broksforge.explorer.kinds;

import com.broksforge.kernel.api.Kind;
import com.broksforge.kernel.api.Ref;
import com.broksforge.kernel.api.Revision;
import com.broksforge.kernel.api.RevisionHash;
import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.explorer.Verbs;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds {@link Kind#DECISION} revisions that satisfy the Decision Law.
 *
 * <p>MANIFESTO Law 6 (ADR-V2-0004, DOMAIN_MODEL §3.4) states: "Every decision cites the claims it
 * rests on — or explicitly declares itself a judgment call." The Kernel Amendment Review accepted
 * KAP-1, so the kernel now enforces this at append: a decision must carry an <em>intent-family</em>
 * reference to the claim(s) it rests on, or declare {@code "judgment-call": true}. This builder offers
 * exactly the two lawful shapes — {@link #restingOn} (cites claims via {@link Verbs#RESTS_ON}, an
 * intent-family verb) and {@link #judgmentCall} — and fails fast in userspace before the append.
 */
public final class Decisions {

    private Decisions() {
    }

    /**
     * A decision that rests on one or more claims (evidence family {@link Verbs#RESTS_ON} refs).
     *
     * @param subtype   the decision subtype (e.g. {@code promotion}, {@code rollback})
     * @param statement what was decided
     * @param claims    the claims this decision rests on; must be non-empty
     * @return the decision revision
     * @throws IllegalArgumentException if the statement is blank or no claims are cited
     */
    public static Revision restingOn(String subtype, String statement, List<RevisionHash> claims) {
        requireText(statement, "decision statement");
        if (claims == null || claims.isEmpty()) {
            throw new IllegalArgumentException(
                    "Decision Law: a decision must cite the claims it rests on, or declare a judgment call");
        }
        List<Ref> refs = new ArrayList<>(claims.size());
        for (RevisionHash claim : claims) {
            if (claim == null) {
                throw new IllegalArgumentException("Decision Law: cited claim must not be null");
            }
            refs.add(Ref.of(Verbs.RESTS_ON, claim));
        }
        CanonicalValue payload = CanonicalValue.objectBuilder()
                .put("statement", statement)
                .build();
        return Revision.of(Kind.DECISION, subtype, payload, refs);
    }

    /**
     * A decision explicitly declared as a judgment call — lawful with no cited claims, provided the
     * rationale is recorded.
     *
     * @param subtype   the decision subtype
     * @param statement what was decided
     * @param rationale why, absent citable claims
     * @return the decision revision
     * @throws IllegalArgumentException if the statement or rationale is blank
     */
    public static Revision judgmentCall(String subtype, String statement, String rationale) {
        requireText(statement, "decision statement");
        requireText(rationale, "judgment-call rationale");
        CanonicalValue payload = CanonicalValue.objectBuilder()
                .put("statement", statement)
                .put("rationale", rationale)
                .put("judgment-call", true)   // canonical key the kernel's Decision Law checks
                .build();
        return Revision.of(Kind.DECISION, subtype, payload, List.of());
    }

    private static void requireText(String s, String what) {
        if (s == null || s.isBlank()) {
            throw new IllegalArgumentException("Decision Law: " + what + " must not be blank");
        }
    }
}
