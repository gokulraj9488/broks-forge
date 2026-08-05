package com.broksforge.knowledge;

import com.broksforge.kernel.api.ActorId;
import com.broksforge.kernel.api.OrgId;
import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.kernel.core.engine.ForgeKernel;
import com.broksforge.kernel.core.engine.Kernels;
import com.broksforge.knowledge.graph.KnowledgeGraph;
import com.broksforge.knowledge.ontology.Ontologies;

import java.util.UUID;

/** Shared test helpers: a fresh in-memory kernel + a KnowledgeGraph over the canonical ontology. */
final class TestSupport {

    static final OrgId ORG = OrgId.of(UUID.fromString("0000f00d-0000-4000-8000-000000000002"));
    static final ActorId ACTOR = ActorId.of("engineer:kn-test");

    private TestSupport() {
    }

    static KnowledgeGraph graph() {
        ForgeKernel kernel = Kernels.inMemory();
        return KnowledgeGraph.open(kernel, ORG, ACTOR, Ontologies.forge());
    }

    static CanonicalValue obj(String k, String v) {
        return CanonicalValue.objectBuilder().put(k, v).build();
    }

    static CanonicalValue num(String key, long value) {
        return CanonicalValue.objectBuilder().put(key, CanonicalValue.of(value)).build();
    }
}
