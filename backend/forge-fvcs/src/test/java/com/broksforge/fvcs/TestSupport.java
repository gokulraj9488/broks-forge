package com.broksforge.fvcs;

import com.broksforge.kernel.api.ActorId;
import com.broksforge.kernel.api.OrgId;
import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.kernel.core.engine.Kernels;
import com.broksforge.knowledge.graph.KnowledgeGraph;
import com.broksforge.knowledge.graph.KnowledgeObject;
import com.broksforge.knowledge.graph.Link;
import com.broksforge.knowledge.ontology.ObjectTypes;
import com.broksforge.knowledge.ontology.Verbs;
import com.broksforge.fvcs.repo.Repository;

import java.util.UUID;

/** Test helpers: a repository on a fresh in-memory kernel + knowledge-object builders. */
final class TestSupport {

    static final OrgId ORG = OrgId.of(UUID.fromString("0000c0de-0000-4000-8000-000000000003"));
    static final ActorId ACTOR = ActorId.of("engineer:fvcs-test");

    private TestSupport() {
    }

    static Repository repo() {
        return Repository.open(Kernels.inMemory(), ORG, ACTOR);
    }

    static KnowledgeObject provider(KnowledgeGraph kg, String name) {
        return kg.define(ObjectTypes.PROVIDER, CanonicalValue.objectBuilder().put("name", name).build());
    }

    static KnowledgeObject model(KnowledgeGraph kg, KnowledgeObject provider, String id) {
        return kg.define(ObjectTypes.MODEL, CanonicalValue.objectBuilder().put("model_id", id).build(),
                Link.of(Verbs.USES, provider));
    }

    static KnowledgeObject prompt(KnowledgeGraph kg, String text) {
        return kg.define(ObjectTypes.PROMPT, CanonicalValue.objectBuilder().put("text", text).build());
    }

    static KnowledgeObject revisePrompt(KnowledgeGraph kg, KnowledgeObject prompt, String text) {
        return kg.addRevision(prompt, CanonicalValue.objectBuilder().put("text", text).build());
    }

    static KnowledgeObject reviseModel(KnowledgeGraph kg, KnowledgeObject model, KnowledgeObject provider, String id) {
        return kg.addRevision(model, CanonicalValue.objectBuilder().put("model_id", id).build(),
                Link.of(Verbs.USES, provider));
    }
}
