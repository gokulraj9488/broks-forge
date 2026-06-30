package com.broksforge.modules.evaluation.domain;

import com.broksforge.common.domain.SoftDeletableEntity;
import com.broksforge.common.persistence.JsonMetadataConverter;
import com.broksforge.modules.agent.domain.LlmProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The top-level evaluation aggregate (see ADR 0005). A job pins the exact inputs it
 * ran against — agent (+version), dataset (+version), optional prompt (+version),
 * optional profile and model — so its results are reproducible and comparable. It
 * fans out to many {@link EvaluationRun}s (one per dataset item), each with many
 * {@link EvaluationResult}s (one per metric), and carries a precomputed summary.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "evaluation_jobs",
        indexes = {
                @Index(name = "idx_eval_jobs_project", columnList = "project_id"),
                @Index(name = "idx_eval_jobs_org", columnList = "organization_id"),
                @Index(name = "idx_eval_jobs_agent", columnList = "agent_id"),
                @Index(name = "idx_eval_jobs_dataset", columnList = "dataset_id"),
                @Index(name = "idx_eval_jobs_status", columnList = "status"),
                @Index(name = "idx_eval_jobs_created", columnList = "created_at")
        }
)
public class EvaluationJob extends SoftDeletableEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private EvaluationStatus status = EvaluationStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 32)
    private EvaluationTargetType targetType = EvaluationTargetType.AGENT;

    @Column(name = "agent_id", nullable = false)
    private UUID agentId;

    @Column(name = "agent_version_id")
    private UUID agentVersionId;

    @Column(name = "dataset_id", nullable = false)
    private UUID datasetId;

    @Column(name = "dataset_version_id", nullable = false)
    private UUID datasetVersionId;

    @Column(name = "prompt_id")
    private UUID promptId;

    @Column(name = "prompt_version_id")
    private UUID promptVersionId;

    @Column(name = "profile_id")
    private UUID profileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 48)
    private LlmProvider provider;

    @Column(name = "model", length = 128)
    private String model;

    @Convert(converter = JsonMetadataConverter.class)
    @Column(name = "parameters", columnDefinition = "text")
    private Map<String, Object> parameters = new LinkedHashMap<>();

    @Column(name = "total_items", nullable = false)
    private int totalItems = 0;

    @Column(name = "completed_items", nullable = false)
    private int completedItems = 0;

    @Column(name = "failed_items", nullable = false)
    private int failedItems = 0;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Convert(converter = JsonMetadataConverter.class)
    @Column(name = "summary", columnDefinition = "text")
    private Map<String, Object> summary = new LinkedHashMap<>();

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    public boolean isRunnable() {
        return status == EvaluationStatus.PENDING;
    }
}
