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
import java.util.UUID;

/**
 * The score of a single metric for a single {@link EvaluationRun}. The
 * {@code evaluationJobId} is denormalised onto the result so job-wide metric
 * aggregations (analytics, benchmarking) avoid a join through runs.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "evaluation_results",
        indexes = {
                @Index(name = "idx_eval_results_run", columnList = "evaluation_run_id"),
                @Index(name = "idx_eval_results_job", columnList = "evaluation_job_id"),
                @Index(name = "idx_eval_results_job_metric", columnList = "evaluation_job_id, metric_type")
        }
)
public class EvaluationResult extends BaseEntity {

    @Column(name = "evaluation_run_id", nullable = false)
    private UUID evaluationRunId;

    @Column(name = "evaluation_job_id", nullable = false)
    private UUID evaluationJobId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric_type", nullable = false, length = 48)
    private EvaluationMetricType metricType;

    @Column(name = "metric_label", length = 120)
    private String metricLabel;

    /** Null when {@link #executionStatus} isn't {@link MetricExecutionStatus#COMPLETED} — the metric never ran. */
    @Column(name = "passed")
    private Boolean passed;

    /** Null when {@link #executionStatus} isn't {@link MetricExecutionStatus#COMPLETED} — the metric never ran. */
    @Column(name = "score", precision = 7, scale = 4)
    private BigDecimal score;

    @Column(name = "threshold", precision = 18, scale = 6)
    private BigDecimal threshold;

    @Column(name = "detail", length = 1000)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_status", nullable = false, length = 40)
    private MetricExecutionStatus executionStatus = MetricExecutionStatus.COMPLETED;
}
