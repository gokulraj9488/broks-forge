package com.broksforge.modules.project.repository;

import com.broksforge.modules.project.domain.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    Optional<Project> findByIdAndOrganizationIdAndDeletedFalse(UUID id, UUID organizationId);

    Page<Project> findByOrganizationIdAndDeletedFalse(UUID organizationId, Pageable pageable);

    boolean existsByOrganizationIdAndSlugIgnoreCaseAndDeletedFalse(UUID organizationId, String slug);

    long countByOrganizationIdAndDeletedFalse(UUID organizationId);
}
