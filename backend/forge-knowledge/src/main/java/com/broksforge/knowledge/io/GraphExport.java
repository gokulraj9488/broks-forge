package com.broksforge.knowledge.io;

import com.broksforge.kernel.api.canonical.CanonicalSerializer;
import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.knowledge.graph.KnowledgeObject;
import com.broksforge.knowledge.graph.KnowledgeView;

import java.util.ArrayList;
import java.util.List;

/**
 * Exports a {@link KnowledgeView} (a projected subgraph) to a {@link CanonicalValue}. Export is a pure
 * read; the deterministic canonical encoding means two exports of the same state are byte-identical.
 * Import is the inverse — replaying objects through {@code KnowledgeGraph.define} so they are re-validated
 * and re-signed as ordinary appends (Law 9); the foundation ships export and the validated define path.
 */
public final class GraphExport {

    private GraphExport() {
    }

    /**
     * @param view a projected knowledge graph
     * @return its canonical value document (objects + relationships)
     */
    public static CanonicalValue toCanonical(KnowledgeView view) {
        List<CanonicalValue> objects = new ArrayList<>();
        for (KnowledgeObject o : view.allObjects()) {
            objects.add(CanonicalValue.objectBuilder()
                    .put("type", o.type().name())
                    .put("node", o.node().toString())
                    .put("hash", o.hash().toString())
                    .put("payload", o.payload())
                    .build());
        }
        List<CanonicalValue> rels = new ArrayList<>();
        for (KnowledgeView.Relationship r : view.relationships()) {
            rels.add(CanonicalValue.objectBuilder()
                    .put("from", r.from().toUri())
                    .put("fromType", r.fromType() != null ? r.fromType().name() : "?")
                    .put("verb", r.verb().name())
                    .put("family", r.family().wireName())
                    .put("to", r.to() != null ? r.to().toUri() : "?")
                    .put("toType", r.toType() != null ? r.toType().name() : "?")
                    .put("intrinsic", r.intrinsic())
                    .build());
        }
        return CanonicalValue.objectBuilder()
                .put("objects", CanonicalValue.array(objects))
                .put("relationships", CanonicalValue.array(rels))
                .build();
    }

    /**
     * @param view a projected knowledge graph
     * @return the deterministic canonical bytes of its document
     */
    public static byte[] toBytes(KnowledgeView view) {
        return CanonicalSerializer.toBytes(toCanonical(view));
    }
}
