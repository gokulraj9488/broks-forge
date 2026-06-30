package com.broksforge.modules.benchmark.repository;

import com.broksforge.modules.benchmark.domain.Benchmark;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BenchmarkRepository extends JpaRepository<Benchmark, UUID> {

    Optional<Benchmark> findByIdAndProjectIdAndOrganizationIdAndDeletedFalse(
            UUID id, UUID projectId, UUID organizationId);

    Page<Benchmark> findByProjectIdAndDeletedFalseOrderByCreatedAtDesc(UUID projectId, Pageable pageable);

    long countByProjectIdAndDeletedFalse(UUID projectId);
}
