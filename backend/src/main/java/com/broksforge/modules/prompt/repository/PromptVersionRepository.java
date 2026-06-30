package com.broksforge.modules.prompt.repository;

import com.broksforge.modules.prompt.domain.PromptVersion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PromptVersionRepository extends JpaRepository<PromptVersion, UUID> {

    Page<PromptVersion> findByPromptIdOrderByVersionNumberDesc(UUID promptId, Pageable pageable);

    Optional<PromptVersion> findByIdAndPromptId(UUID id, UUID promptId);

    Optional<PromptVersion> findFirstByPromptIdOrderByVersionNumberDesc(UUID promptId);

    List<PromptVersion> findByPromptIdAndActiveTrue(UUID promptId);
}
