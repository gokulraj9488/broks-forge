package com.broksforge.modules.report.repository;

import com.broksforge.modules.report.domain.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReportRepository extends JpaRepository<Report, UUID> {

    Optional<Report> findByIdAndProjectIdAndOrganizationIdAndDeletedFalse(UUID id, UUID projectId, UUID organizationId);

    Page<Report> findByProjectIdAndDeletedFalseOrderByCreatedAtDesc(UUID projectId, Pageable pageable);

    List<Report> findTop10ByProjectIdAndDeletedFalseOrderByCreatedAtDesc(UUID projectId);
}
