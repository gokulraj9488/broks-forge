package com.broksforge.modules.knowledge.domain;

import com.broksforge.common.domain.BaseEntity;
import com.broksforge.common.persistence.JsonStringListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * A node in the Engineering Knowledge Graph (ADR 0013): one piece of curated
 * engineering knowledge — a failure mode, regression, recommendation or optimisation.
 *
 * <p>The graph is platform-global reference data (not tenant-scoped): it encodes how
 * the platform reasons about agent quality, and is shared by every project. Nodes are
 * addressed by a stable {@code nodeKey} so the advisor and root-cause engines can link
 * a finding to canonical knowledge without coupling to a row id. {@code occurrenceCount}
 * is the seam for future learning: how often the platform has observed this pattern.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "knowledge_nodes",
        uniqueConstraints = @UniqueConstraint(name = "uq_knowledge_nodes_key", columnNames = "node_key"),
        indexes = {
                @Index(name = "idx_knowledge_nodes_type", columnList = "node_type"),
                @Index(name = "idx_knowledge_nodes_category", columnList = "category")
        }
)
public class KnowledgeNode extends BaseEntity {

    @Column(name = "node_key", nullable = false, length = 80)
    private String nodeKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "node_type", nullable = false, length = 32)
    private KnowledgeNodeType nodeType;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** Engineering domain: PROMPT, RAG, AGENT, MODEL, COST, RELIABILITY, QUALITY, LATENCY. */
    @Column(name = "category", nullable = false, length = 48)
    private String category;

    @Column(name = "summary", columnDefinition = "text")
    private String summary;

    /** How the platform detects this pattern (human-readable heuristic description). */
    @Column(name = "detection_hint", columnDefinition = "text")
    private String detectionHint;

    /** The remediation: the HOW-TO-FIX guidance. */
    @Column(name = "remediation", columnDefinition = "text")
    private String remediation;

    @Column(name = "expected_improvement", length = 300)
    private String expectedImprovement;

    @Column(name = "default_severity", nullable = false, length = 16)
    private String defaultSeverity;

    @Column(name = "default_confidence", nullable = false, length = 16)
    private String defaultConfidence;

    @Convert(converter = JsonStringListConverter.class)
    @Column(name = "tags", columnDefinition = "text")
    private List<String> tags = new ArrayList<>();

    /** How many times the platform has observed this pattern — the learning seam. */
    @Column(name = "occurrence_count", nullable = false)
    private long occurrenceCount = 0;
}
