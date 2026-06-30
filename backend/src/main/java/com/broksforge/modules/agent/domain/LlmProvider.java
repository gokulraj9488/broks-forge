package com.broksforge.modules.agent.domain;

/**
 * The LLM provider backing a specific agent version. Descriptive metadata used
 * by future evaluation, benchmarking and analytics modules.
 */
public enum LlmProvider {

    OPENAI,
    ANTHROPIC,
    AZURE_OPENAI,
    AWS_BEDROCK,
    GOOGLE_VERTEX,
    GOOGLE_GEMINI,
    COHERE,
    MISTRAL,
    META_LLAMA,
    OLLAMA,
    HUGGINGFACE,
    CUSTOM,
    OTHER
}
