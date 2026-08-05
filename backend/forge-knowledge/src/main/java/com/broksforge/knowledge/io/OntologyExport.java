package com.broksforge.knowledge.io;

import com.broksforge.kernel.api.canonical.CanonicalSerializer;
import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.knowledge.ontology.ObjectType;
import com.broksforge.knowledge.ontology.Ontology;
import com.broksforge.knowledge.ontology.PayloadField;
import com.broksforge.knowledge.ontology.RelationType;

import java.util.ArrayList;
import java.util.List;

/**
 * Exports an {@link Ontology} to a {@link CanonicalValue} — a portable, diffable, hashable ontology
 * document. Because the encoding is the kernel's canonical (RFC 8785-profile) form, two exports of the
 * same ontology are byte-identical, so the ontology itself can be content-addressed and versioned like
 * any other Forge value.
 */
public final class OntologyExport {

    private OntologyExport() {
    }

    /**
     * @param ontology the ontology
     * @return its canonical value document
     */
    public static CanonicalValue toCanonical(Ontology ontology) {
        List<CanonicalValue> types = new ArrayList<>();
        for (ObjectType t : ontology.objectTypes()) {
            List<CanonicalValue> fields = new ArrayList<>();
            for (PayloadField f : t.schema().fields()) {
                fields.add(CanonicalValue.objectBuilder()
                        .put("key", f.key())
                        .put("type", f.type().name())
                        .put("required", f.required())
                        .build());
            }
            List<CanonicalValue> roles = t.schema().roles().stream()
                    .map(CanonicalValue::of).map(CanonicalValue.class::cast).toList();
            types.add(CanonicalValue.objectBuilder()
                    .put("name", t.name())
                    .put("kind", t.kind().wireName())
                    .put("subtype", t.subtype())
                    .put("fields", CanonicalValue.array(fields))
                    .put("roles", CanonicalValue.array(roles))
                    .build());
        }

        List<CanonicalValue> relations = new ArrayList<>();
        for (RelationType r : ontology.relationTypes()) {
            relations.add(CanonicalValue.objectBuilder()
                    .put("verb", r.verb().name())
                    .put("family", r.family().wireName())
                    .put("from", r.fromType() != null ? r.fromType() : "*")
                    .put("fromKind", r.fromKind() != null ? r.fromKind().wireName() : "*")
                    .put("to", r.toType() != null ? r.toType() : (r.toTypes().isEmpty() ? "*" : String.join("|", r.toTypes())))
                    .put("toKind", r.toKind() != null ? r.toKind().wireName() : "*")
                    .put("cardinality", r.cardinality().name())
                    .put("intrinsic", r.intrinsic())
                    .build());
        }

        return CanonicalValue.objectBuilder()
                .put("objectTypes", CanonicalValue.array(types))
                .put("relationTypes", CanonicalValue.array(relations))
                .build();
    }

    /**
     * @param ontology the ontology
     * @return the deterministic canonical bytes of its document
     */
    public static byte[] toBytes(Ontology ontology) {
        return CanonicalSerializer.toBytes(toCanonical(ontology));
    }
}
