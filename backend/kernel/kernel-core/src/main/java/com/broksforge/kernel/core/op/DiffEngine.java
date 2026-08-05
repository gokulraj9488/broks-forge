package com.broksforge.kernel.core.op;

import com.broksforge.kernel.api.Revision;
import com.broksforge.kernel.api.canonical.CanonicalSerializer;
import com.broksforge.kernel.api.canonical.CanonicalValue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Generic structural diff over canonical content (ADR-V2-0007, op 4). Walks two canonical value
 * trees in parallel and reports every located difference as a {@link Delta.Change} with a
 * JSON-pointer-like path. Because it operates on the same canonical form used for hashing, two
 * revisions with equal hashes always produce an empty delta, and any hash difference produces at
 * least one change.
 */
public final class DiffEngine {

    /**
     * @param a the left revision
     * @param b the right revision
     * @return the structural delta between their canonical forms
     */
    public Delta diff(Revision a, Revision b) {
        return diff(a.canonicalForm(), b.canonicalForm());
    }

    /**
     * @param a the left canonical value
     * @param b the right canonical value
     * @return the structural delta
     */
    public Delta diff(CanonicalValue a, CanonicalValue b) {
        List<Delta.Change> changes = new ArrayList<>();
        walk("", a, b, changes);
        changes.sort(Comparator.comparing(Delta.Change::path));
        return new Delta(changes);
    }

    private void walk(String path, CanonicalValue a, CanonicalValue b, List<Delta.Change> changes) {
        if (a == null && b == null) {
            return;
        }
        if (a == null) {
            changes.add(new Delta.Change(pathOrRoot(path), Delta.Kind.ADDED, null, str(b)));
            return;
        }
        if (b == null) {
            changes.add(new Delta.Change(pathOrRoot(path), Delta.Kind.REMOVED, str(a), null));
            return;
        }
        if (a.equals(b)) {
            return;
        }
        if (a instanceof CanonicalValue.Obj oa && b instanceof CanonicalValue.Obj ob) {
            Set<String> keys = new LinkedHashSet<>();
            keys.addAll(oa.entries().keySet());
            keys.addAll(ob.entries().keySet());
            for (String key : keys) {
                walk(path + "/" + key, oa.entries().get(key), ob.entries().get(key), changes);
            }
            return;
        }
        if (a instanceof CanonicalValue.Arr aa && b instanceof CanonicalValue.Arr ba) {
            int max = Math.max(aa.items().size(), ba.items().size());
            for (int i = 0; i < max; i++) {
                CanonicalValue ai = i < aa.items().size() ? aa.items().get(i) : null;
                CanonicalValue bi = i < ba.items().size() ? ba.items().get(i) : null;
                walk(path + "/" + i, ai, bi, changes);
            }
            return;
        }
        // Different variant types, or two unequal scalars.
        changes.add(new Delta.Change(pathOrRoot(path), Delta.Kind.CHANGED, str(a), str(b)));
    }

    private static String pathOrRoot(String path) {
        return path.isEmpty() ? "/" : path;
    }

    private static String str(CanonicalValue v) {
        return v == null ? null : CanonicalSerializer.toCanonicalString(v);
    }
}
