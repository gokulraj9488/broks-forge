package com.broksforge.modules.knowledge.service;

/**
 * A lightweight, published view of a {@link com.broksforge.modules.knowledge.domain.KnowledgeNode}
 * for cross-module consumers (the advisor and root-cause engines). It carries just
 * the canonical knowledge needed to enrich a finding — without leaking the entity or
 * the knowledge module's persistence.
 *
 * @param nodeKey             stable identifier of the knowledge node
 * @param title              human-readable name of the pattern
 * @param category           engineering domain (PROMPT, RAG, AGENT, MODEL, COST, ...)
 * @param remediation        canonical how-to-fix guidance
 * @param expectedImprovement canonical expected-improvement statement
 * @param defaultSeverity    suggested severity (advisor may override per-context)
 * @param defaultConfidence  suggested confidence (advisor may override per-context)
 */
public record KnowledgePattern(
        String nodeKey,
        String title,
        String category,
        String remediation,
        String expectedImprovement,
        String defaultSeverity,
        String defaultConfidence
) {
}
