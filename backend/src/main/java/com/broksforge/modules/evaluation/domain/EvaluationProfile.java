package com.broksforge.modules.evaluation.domain;

import com.broksforge.common.domain.SoftDeletableEntity;
import com.broksforge.modules.evaluation.persistence.MetricSpecListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
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
// (project_id, slug) uniqueness is enforced by a PARTIAL unique index
// (WHERE deleted = false) defined in migration V30, so a slug frees up once its
// profile is soft-deleted. JPA's @UniqueConstraint cannot express a partial index,
// so it is intentionally omitted here — the migration is the source of truth.
@Table(
        name = "evaluation_profiles",
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

    /** Whether this profile may currently be selected for new evaluation jobs. */
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    /** Highest {@code EvaluationProfileVersion.versionNumber} created for this profile so far. */
    @Column(name = "latest_version_number", nullable = false)
    private int latestVersionNumber = 0;

    /** The version whose metrics/passThreshold new jobs pin to; null only before the first version exists. */
    @Column(name = "current_version_id")
    private UUID currentVersionId;
}
