package com.broksforge.modules.prompt.repository;

import com.broksforge.modules.prompt.domain.Prompt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PromptRepository extends JpaRepository<Prompt, UUID>, JpaSpecificationExecutor<Prompt> {

    Optional<Prompt> findByIdAndProjectIdAndOrganizationIdAndDeletedFalse(UUID id, UUID projectId, UUID organizationId);

    boolean existsByProjectIdAndSlugIgnoreCaseAndDeletedFalse(UUID projectId, String slug);

    long countByProjectIdAndDeletedFalse(UUID projectId);
}
