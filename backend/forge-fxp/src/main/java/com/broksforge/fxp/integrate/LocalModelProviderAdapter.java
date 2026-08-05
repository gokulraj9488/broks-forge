package com.broksforge.fxp.integrate;

import com.broksforge.fxp.studio.StudioService;
import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.knowledge.graph.KnowledgeObject;
import com.broksforge.knowledge.graph.Link;
import com.broksforge.knowledge.ontology.ObjectTypes;
import com.broksforge.knowledge.ontology.Verbs;

/**
 * Reference model-provider adapter (stands in for OpenAI/Anthropic/Ollama). It records the outcome of a
 * model invocation as a {@code Run} observation that {@code executed} the given agent — turning an external,
 * non-deterministic model call into a first-class, attributed platform fact. Real transports subclass or
 * wrap this; the mapping to a platform fact is identical.
 */
public final class LocalModelProviderAdapter implements ModelProviderAdapter {

    @Override
    public KnowledgeObject record(StudioService studio, KnowledgeObject agent, ModelInvocation invocation) {
        CanonicalValue payload = CanonicalValue.objectBuilder()
                .put("status", invocation.status())
                .build();
        return studio.recordObservation(ObjectTypes.RUN, payload, Link.of(Verbs.EXECUTED, agent));
    }
}
