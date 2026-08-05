package com.broksforge.fvcs.compat;

import com.broksforge.fvcs.diff.ChangeKind;
import com.broksforge.fvcs.diff.ChangeSet;
import com.broksforge.fvcs.diff.DiffEngine;
import com.broksforge.fvcs.diff.ObjectChange;
import com.broksforge.fvcs.repo.SnapshotRef;

import java.util.ArrayList;
import java.util.List;

/**
 * Structural compatibility: can snapshot B replace snapshot A? The foundation checks structural
 * replaceability — an object present in A that is gone in B is a potential incompatibility (something a
 * consumer relied on has disappeared); changed/added objects are noted for re-evaluation. Semantic and
 * runtime compatibility (capability claims, policy/cost budgets of a target Environment) are a documented
 * extension; the repository records the result as a {@code CompatibilityVerdict} Claim.
 */
public final class CompatibilityEngine {

    private final DiffEngine diff;

    /** @param diff the diff engine (indexed over the org) */
    public CompatibilityEngine(DiffEngine diff) {
        this.diff = diff;
    }

    /**
     * @param from the current/required snapshot (A)
     * @param to   the candidate replacement snapshot (B)
     * @return the structural compatibility result
     */
    public CompatibilityResult check(SnapshotRef from, SnapshotRef to) {
        ChangeSet cs = diff.diff(from, to);
        List<String> issues = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        for (ObjectChange c : cs.changes()) {
            String type = c.type() != null ? c.type().name() : "object";
            if (c.kind() == ChangeKind.REMOVED) {
                issues.add(type + " " + shortNode(c) + " present in A is absent in B (removed capability)");
            } else if (c.kind() == ChangeKind.CHANGED) {
                notes.add(type + " " + shortNode(c) + " changed; re-evaluation recommended");
            } else if (c.kind() == ChangeKind.ADDED) {
                notes.add(type + " " + shortNode(c) + " added in B");
            }
        }
        return new CompatibilityResult(issues.isEmpty(), issues, notes);
    }

    private static String shortNode(ObjectChange c) {
        String s = c.node().toString();
        return s.length() >= 8 ? s.substring(0, 8) : s;
    }
}
