package com.broksforge.modules.benchmark.domain;

import com.broksforge.common.domain.SoftDeletableEntity;
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
 * A named comparison of evaluation jobs. Each entry ({@link BenchmarkEntry}) points
 * at one evaluation job; the leaderboard ranks them by a chosen summary metric. By
 * building on completed evaluation-job summaries, benchmarking inherits the
 * reproducibility of the underlying jobs (see ADR 0005).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "benchmarks",
        indexes = {
                @Index(name = "idx_benchmarks_project", columnList = "project_id"),
                @Index(name = "idx_benchmarks_org", columnList = "organization_id")
        }
)
public class Benchmark extends SoftDeletableEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 48)
    private BenchmarkType type;

    /** The summary metric ranked on (e.g. passRate, avgScore, avgLatencyMs, totalCost). */
    @Column(name = "metric_key", nullable = false, length = 64)
    private String metricKey = "passRate";
}
