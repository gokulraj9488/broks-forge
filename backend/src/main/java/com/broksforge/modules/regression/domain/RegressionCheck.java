package com.broksforge.modules.regression.domain;

import com.broksforge.common.domain.SoftDeletableEntity;
import com.broksforge.common.persistence.JsonMetadataConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A point-in-time comparison of a candidate evaluation job against a baseline,
 * detecting per-dimension regressions (latency, cost, tokens, quality, score) beyond
 * a tolerance. Findings are computed once at creation and frozen, so a regression
 * alert is a stable record even as the underlying jobs age.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "regression_checks",
        indexes = {
                @Index(name = "idx_regression_checks_project", columnList = "project_id"),
                @Index(name = "idx_regression_checks_org", columnList = "organization_id"),
                @Index(name = "idx_regression_checks_regressed", columnList = "regressed")
        }
)
public class RegressionCheck extends SoftDeletableEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "baseline_job_id", nullable = false)
    private UUID baselineJobId;

    @Column(name = "candidate_job_id", nullable = false)
    private UUID candidateJobId;

    @Column(name = "tolerance_pct", nullable = false, precision = 6, scale = 3)
    private BigDecimal tolerancePct = BigDecimal.TEN;

    @Column(name = "regressed", nullable = false)
    private boolean regressed = false;

    /** Per-dimension findings: metric -> {label, baseline, candidate, deltaPct, regressed, lowerIsBetter}. */
    @Convert(converter = JsonMetadataConverter.class)
    @Column(name = "findings", columnDefinition = "text")
    private Map<String, Object> findings = new LinkedHashMap<>();
}
