package com.broksforge.fkge.index;

import com.broksforge.kernel.api.ActorId;
import com.broksforge.kernel.api.Kind;
import com.broksforge.kernel.api.LogPosition;
import com.broksforge.kernel.api.NodeId;
import com.broksforge.kernel.api.RevisionHash;
import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.knowledge.ontology.ObjectType;

import java.util.Optional;

/**
 * A node of the reasoning graph — the latest revision of a continuant, projected from the kernel log.
 *
 * <p>Purely a read view: identity ({@link NodeId}), epistemic {@link Kind}, the ontology {@link ObjectType}
 * (may be absent for untyped subtypes), the content-addressed {@link RevisionHash}, the causal
 * {@link LogPosition}, the authoring {@link ActorId} (provenance), and the payload. FKGE stores nothing of
 * its own; a {@code GraphNode} is a deterministic function of an immutable log prefix.
 */
public record GraphNode(NodeId id,
                        Kind kind,
                        String subtype,
                        ObjectType type,
                        RevisionHash hash,
                        LogPosition position,
                        ActorId author,
                        CanonicalValue payload) {

    public GraphNode {
        if (id == null) throw new IllegalArgumentException("id");
        if (kind == null) throw new IllegalArgumentException("kind");
        if (hash == null) throw new IllegalArgumentException("hash");
        if (position == null) throw new IllegalArgumentException("position");
    }

    /** The ontology type name if this node is typed, else empty (untyped subtype). */
    public Optional<String> typeName() {
        return type == null ? Optional.empty() : Optional.of(type.name());
    }

    /** A stable label for display and signatures: the type name, else {@code kind/subtype}. */
    public String label() {
        return type != null ? type.name() : kind + "/" + subtype;
    }
}
