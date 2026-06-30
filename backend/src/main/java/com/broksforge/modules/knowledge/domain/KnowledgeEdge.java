package com.broksforge.modules.knowledge.domain;

import com.broksforge.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * A directed edge in the Engineering Knowledge Graph (ADR 0013), connecting two
 * {@link KnowledgeNode}s with a typed {@link KnowledgeRelation} (e.g. a failure mode
 * {@code MITIGATED_BY} a recommendation). The {@code weight} expresses relative
 * strength and is the seam future learning uses to reinforce edges.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "knowledge_edges",
        indexes = {
                @Index(name = "idx_knowledge_edges_source", columnList = "source_node_id"),
                @Index(name = "idx_knowledge_edges_target", columnList = "target_node_id")
        }
)
public class KnowledgeEdge extends BaseEntity {

    @Column(name = "source_node_id", nullable = false)
    private UUID sourceNodeId;

    @Column(name = "target_node_id", nullable = false)
    private UUID targetNodeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation", nullable = false, length = 32)
    private KnowledgeRelation relation;

    @Column(name = "weight", nullable = false)
    private int weight = 1;
}
