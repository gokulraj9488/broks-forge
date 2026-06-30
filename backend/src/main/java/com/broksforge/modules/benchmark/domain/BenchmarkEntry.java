package com.broksforge.modules.benchmark.domain;

import com.broksforge.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * One competitor in a {@link Benchmark}: a reference (by id) to an evaluation job and
 * a display label. The job's summary supplies the metrics ranked on the leaderboard.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "benchmark_entries",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_benchmark_entries_job", columnNames = {"benchmark_id", "evaluation_job_id"}),
        indexes = @Index(name = "idx_benchmark_entries_benchmark", columnList = "benchmark_id")
)
public class BenchmarkEntry extends BaseEntity {

    @Column(name = "benchmark_id", nullable = false)
    private UUID benchmarkId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "evaluation_job_id", nullable = false)
    private UUID evaluationJobId;

    @Column(name = "label", nullable = false, length = 160)
    private String label;
}
