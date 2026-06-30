package com.broksforge.modules.knowledge.domain;

/**
 * The kind of node in the Engineering Knowledge Graph. The graph is a curated,
 * extensible catalogue of engineering knowledge — failure modes, regressions,
 * recommendations and optimisations — that the advisor and root-cause engines link
 * their findings to, and that future learning can grow (see ADR 0013).
 */
public enum KnowledgeNodeType {
    /** A way an agent execution can fail (e.g. empty output, timeout). */
    FAILURE_MODE,
    /** A way a metric can degrade between versions (e.g. cost spike). */
    REGRESSION,
    /** A remediation that mitigates one or more failure modes / regressions. */
    RECOMMENDATION,
    /** An improvement opportunity that is not strictly a failure (e.g. model overkill). */
    OPTIMIZATION
}
