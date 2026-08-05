package com.broksforge.modules.prompt.repository;

import com.broksforge.modules.prompt.domain.Prompt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PromptRepository extends JpaRepository<Prompt, UUID>, JpaSpecificationExecutor<Prompt> {

    Optional<Prompt> findByIdAndProjectIdAndOrganizationIdAndDeletedFalse(UUID id, UUID projectId, UUID organizationId);

    /** All non-deleted prompts in an organization (read-only; used to assemble the engineering graph). */
    List<Prompt> findByOrganizationIdAndDeletedFalse(UUID organizationId);

    boolean existsByProjectIdAndSlugIgnoreCaseAndDeletedFalse(UUID projectId, String slug);

    long countByProjectIdAndDeletedFalse(UUID projectId);
}
