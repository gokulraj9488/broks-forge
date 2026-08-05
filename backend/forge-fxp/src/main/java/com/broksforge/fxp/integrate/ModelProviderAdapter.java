package com.broksforge.fxp.integrate;

import com.broksforge.fxp.studio.StudioService;
import com.broksforge.knowledge.graph.KnowledgeObject;

/**
 * Adapter SPI for model providers (OpenAI, Anthropic, Ollama). An adapter runs a model outside the platform
 * and records the result as a Forge {@code Run} observation via the public write API. The platform depends
 * on none of these providers; the adapter depends on the platform.
 */
public interface ModelProviderAdapter {

    /** Record a model invocation against an agent as a {@code Run} observation. */
    KnowledgeObject record(StudioService studio, KnowledgeObject agent, ModelInvocation invocation);
}
