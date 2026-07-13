package com.broksforge.modules.evaluation.domain;

import com.broksforge.common.domain.BaseEntity;
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
 * An immutable snapshot of an evaluation profile's metrics + pass threshold. Once created, a
 * version's metrics never change; editing a profile's scoring config produces a new version.
 * This is what makes an evaluation job reproducible — a job records the exact
 * {@code profileVersionId} it ran against, so later edits to the profile can never change what a
 * historical job's metrics were (see {@code EvaluationJob.profileVersionId}).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "evaluation_profile_versions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_eval_profile_versions_number", columnNames = {"profile_id", "version_number"}),
        indexes = {
                @Index(name = "idx_eval_profile_versions_profile", columnList = "profile_id"),
                @Index(name = "idx_eval_profile_versions_org", columnList = "organization_id")
        }
)
public class EvaluationProfileVersion extends BaseEntity {

    @Column(name = "profile_id", nullable = false)
    private UUID profileId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Convert(converter = MetricSpecListConverter.class)
    @Column(name = "metrics", columnDefinition = "text")
    private List<MetricSpec> metrics = new ArrayList<>();

    @Column(name = "pass_threshold", precision = 7, scale = 4)
    private BigDecimal passThreshold;
}
