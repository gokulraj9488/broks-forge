package com.broksforge.modules.evaluation.domain;

import com.broksforge.common.domain.SoftDeletableEntity;
import com.broksforge.modules.evaluation.persistence.MetricSpecListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A reusable, named set of metrics and thresholds applied by evaluation jobs. A
 * profile keeps the "how do we judge an output" decision in one place so the same
 * rubric can be applied across many jobs, agents and datasets — which is what makes
 * benchmarking and regression comparisons meaningful.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "evaluation_profiles",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_eval_profiles_project_slug", columnNames = {"project_id", "slug"}),
        indexes = {
                @Index(name = "idx_eval_profiles_project", columnList = "project_id"),
                @Index(name = "idx_eval_profiles_org", columnList = "organization_id")
        }
)
public class EvaluationProfile extends SoftDeletableEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "slug", nullable = false, length = 64)
    private String slug;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Convert(converter = MetricSpecListConverter.class)
    @Column(name = "metrics", columnDefinition = "text")
    private List<MetricSpec> metrics = new ArrayList<>();

    /** Minimum overall pass-rate (0..1) for a run to be considered passing; null = all metrics must pass. */
    @Column(name = "pass_threshold", precision = 7, scale = 4)
    private BigDecimal passThreshold;
}
