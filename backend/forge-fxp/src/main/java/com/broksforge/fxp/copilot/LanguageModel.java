package com.broksforge.fxp.copilot;

/**
 * The Copilot's language-model adapter SPI. Its sole responsibility is to <em>narrate</em> a proof the
 * platform has already computed — it never originates engineering truth. Real adapters (OpenAI, Anthropic,
 * Ollama) implement this; they receive only a {@link GroundingContext} of facts, never the graph.
 */
@FunctionalInterface
public interface LanguageModel {
    String narrate(GroundingContext context);
}
