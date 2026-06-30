package com.broksforge.modules.benchmark.repository;

import com.broksforge.modules.benchmark.domain.BenchmarkEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BenchmarkEntryRepository extends JpaRepository<BenchmarkEntry, UUID> {

    List<BenchmarkEntry> findByBenchmarkIdOrderByCreatedAtAsc(UUID benchmarkId);

    Optional<BenchmarkEntry> findByIdAndBenchmarkId(UUID id, UUID benchmarkId);

    boolean existsByBenchmarkIdAndEvaluationJobId(UUID benchmarkId, UUID evaluationJobId);

    long countByBenchmarkId(UUID benchmarkId);
}
