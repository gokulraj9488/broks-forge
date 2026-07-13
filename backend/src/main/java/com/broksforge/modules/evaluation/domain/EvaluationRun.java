package com.broksforge.modules.evaluation.domain;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A single execution within an {@link EvaluationJob}: one dataset item sent to the
 * target, its output, and the captured performance/cost telemetry. Metric scores
 * for this run are stored as {@link EvaluationResult}s.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "evaluation_runs",
        indexes = {
                @Index(name = "idx_eval_runs_job", columnList = "evaluation_job_id"),
                @Index(name = "idx_eval_runs_job_status", columnList = "evaluation_job_id, status"),
                @Index(name = "idx_eval_runs_org", columnList = "organization_id")
        }
)
public class EvaluationRun extends BaseEntity {

    @Column(name = "evaluation_job_id", nullable = false)
    private UUID evaluationJobId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "dataset_item_id")
    private UUID datasetItemId;

    @Column(name = "sequence", nullable = false)
    private int sequence;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private EvaluationRunStatus status = EvaluationRunStatus.PENDING;

    @Column(name = "input", columnDefinition = "text")
    private String input;

    @Column(name = "output", columnDefinition = "text")
    private String output;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "cost", precision = 18, scale = 6)
    private BigDecimal cost;

    @Column(name = "http_status")
    private Integer httpStatus;

    /** Overall pass for this run (all metrics passed, or pass-rate >= profile threshold). */
    @Column(name = "passed")
    private Boolean passed;

    /** Fraction of metrics passed for this run (0..1). */
    @Column(name = "score", precision = 7, scale = 4)
    private BigDecimal score;

    @Column(name = "error", length = 1000)
    private String error;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** Which execution pass produced this run (1 for the first run, 2+ after a resume/retry). */
    @Column(name = "attempt", nullable = false)
    private int attempt = 1;
}
