package com.broksforge.modules.agent.domain;

/**
 * The framework an agent is implemented with. The platform itself is framework
 * agnostic — this is descriptive metadata used for filtering and analytics.
 * New frameworks are added here without a database migration (stored as text).
 */
public enum AgentFramework {

    SPRING_AI,
    LANGGRAPH,
    LANGCHAIN,
    CREWAI,
    AUTOGEN,
    PYDANTIC_AI,
    SEMANTIC_KERNEL,
    LLAMA_INDEX,
    CUSTOM_REST,
    OTHER
}
