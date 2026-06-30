package com.broksforge.modules.advisor.domain;

/**
 * The engineering domain a recommendation belongs to. Mirrors the categories used by
 * the Engineering Knowledge Graph so a recommendation can be linked to canonical
 * knowledge and grouped consistently in the UI.
 */
public enum RecommendationCategory {
    PROMPT,
    RAG,
    AGENT,
    MODEL,
    COST,
    RELIABILITY,
    QUALITY,
    LATENCY
}
