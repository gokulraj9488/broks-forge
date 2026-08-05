package com.broksforge.knowledge.graph;

import com.broksforge.kernel.api.Address;
import com.broksforge.kernel.api.EdgeFamily;
import com.broksforge.kernel.api.NodeId;
import com.broksforge.kernel.api.OrgId;
import com.broksforge.kernel.api.Ref;
import com.broksforge.kernel.api.Revision;
import com.broksforge.kernel.api.RevisionHash;
import com.broksforge.kernel.api.Verb;
import com.broksforge.kernel.core.engine.ForgeKernel;
import com.broksforge.kernel.core.log.EdgeKey;
import com.broksforge.kernel.core.log.LogEntry;
import com.broksforge.kernel.core.log.Payload;
import com.broksforge.knowledge.ontology.ObjectType;
import com.broksforge.knowledge.ontology.Ontology;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A read-only, ontology-typed projection of one organization's knowledge graph, folded from the kernel
 * log. It classifies each node by {@code (kind, subtype)} into its {@link ObjectType}, exposes typed
 * objects and relationships, and surfaces unknown subtypes as {@code untyped} for forward compatibility.
 * Projections are rebuildable derivations (kernel ADR-V2-0001); this is the read side of the layer.
 */
public final class KnowledgeView {

    /** A typed relationship in the projection. */
    public record Relationship(Address from, ObjectType fromType, Verb verb,
                               Address to, ObjectType toType, boolean intrinsic) {
        /** @return the edge family */
        public EdgeFamily family() {
            return verb.family();
        }
    }

    private final Map<NodeId, KnowledgeObject> latestByNode = new LinkedHashMap<>();
    private final List<Relationship> relationships = new ArrayList<>();
    private final Set<String> untypedSubtypes = new LinkedHashSet<>();

    private KnowledgeView() {
    }

    /**
     * Folds an organization's log into a typed projection.
     *
     * @param kernel   the kernel
     * @param org      the organization
     * @param ontology the ontology used to type nodes
     * @return the projection
     */
    public static KnowledgeView of(ForgeKernel kernel, OrgId org, Ontology ontology) {
        KnowledgeView v = new KnowledgeView();
        Map<RevisionHash, Address.Revision> addressByHash = new LinkedHashMap<>();
        Map<RevisionHash, ObjectType> typeByHash = new LinkedHashMap<>();
        Set<EdgeKey> extrinsic = new LinkedHashSet<>();

        for (LogEntry entry : kernel.log(org)) {
            switch (entry.payload()) {
                case Payload.NodePut np -> {
                    Revision rev = np.revision();
                    ObjectType type = ontology.resolve(rev.kind(), rev.subtype()).orElse(null);
                    if (type == null) {
                        v.untypedSubtypes.add(rev.kind().wireName() + "/" + rev.subtype());
                    }
                    Address.Revision addr = new Address.Revision(org, rev.kind(), np.node(), rev.hash());
                    addressByHash.put(rev.hash(), addr);
                    if (type != null) {
                        typeByHash.put(rev.hash(), type);
                        v.latestByNode.put(np.node(), new KnowledgeObject(type, addr, rev));
                    }
                    for (Ref ref : rev.refs()) {
                        Address.Revision targetAddr = addressByHash.get(ref.target());
                        v.relationships.add(new Relationship(addr, type, ref.verb(),
                                targetAddr, targetAddr == null ? null : typeByHash.get(ref.target()), true));
                    }
                }
                case Payload.EdgeAsserted ea -> extrinsic.add(ea.edge());
                case Payload.EdgeRetracted er -> extrinsic.remove(er.edge());
                case Payload.NameRepointed ignored -> { /* names resolved via kernel.resolve */ }
                case Payload.ClockTick ignored -> { /* not a knowledge object */ }
            }
        }
        for (EdgeKey e : extrinsic) {
            v.relationships.add(new Relationship(e.from(), typeOf(e.from(), typeByHash),
                    e.verb(), e.to(), typeOf(e.to(), typeByHash), false));
        }
        return v;
    }

    private static ObjectType typeOf(Address address, Map<RevisionHash, ObjectType> typeByHash) {
        return address instanceof Address.Revision r ? typeByHash.get(r.revision()) : null;
    }

    /** @return all typed objects (latest revision per node), in creation order */
    public List<KnowledgeObject> allObjects() {
        return List.copyOf(latestByNode.values());
    }

    /**
     * @param type an object type
     * @return the objects of that type
     */
    public List<KnowledgeObject> objects(ObjectType type) {
        return latestByNode.values().stream().filter(o -> o.type().equals(type)).toList();
    }

    /**
     * @param node a continuant id
     * @return the latest typed object for it, if any
     */
    public Optional<KnowledgeObject> object(NodeId node) {
        return Optional.ofNullable(latestByNode.get(node));
    }

    /**
     * @param type an object type
     * @return how many objects of that type exist
     */
    public long count(ObjectType type) {
        return objects(type).size();
    }

    /** @return all typed relationships (intrinsic then extrinsic) */
    public List<Relationship> relationships() {
        return List.copyOf(relationships);
    }

    /** @return kind/subtype tokens seen in the log that the ontology does not (yet) know */
    public Set<String> untypedSubtypes() {
        return Set.copyOf(untypedSubtypes);
    }
}
