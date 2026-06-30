package com.broksforge.modules.agent.repository;

import com.broksforge.modules.agent.domain.AgentVersion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentVersionRepository extends JpaRepository<AgentVersion, UUID> {

    Page<AgentVersion> findByAgentId(UUID agentId, Pageable pageable);

    Optional<AgentVersion> findByIdAndAgentId(UUID id, UUID agentId);

    boolean existsByAgentIdAndVersionNumberIgnoreCase(UUID agentId, String versionNumber);

    Optional<AgentVersion> findFirstByAgentIdOrderBySequenceDesc(UUID agentId);

    List<AgentVersion> findByAgentIdAndActiveTrue(UUID agentId);
}
