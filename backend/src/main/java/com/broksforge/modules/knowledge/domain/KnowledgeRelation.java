package com.broksforge.modules.knowledge.domain;

/**
 * The directed relationship a {@link KnowledgeEdge} expresses between two
 * {@link KnowledgeNode}s in the Engineering Knowledge Graph.
 */
public enum KnowledgeRelation {
    /** Source is a cause of the target failure/regression. */
    CAUSES,
    /** Target recommendation mitigates the source failure/regression. */
    MITIGATED_BY,
    /** Source tends to lead to the target over time. */
    LEADS_TO,
    /** General, non-causal association. */
    RELATED_TO
}
