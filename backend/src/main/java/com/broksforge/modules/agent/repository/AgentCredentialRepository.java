package com.broksforge.modules.agent.repository;

import com.broksforge.modules.agent.domain.AgentCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentCredentialRepository extends JpaRepository<AgentCredential, UUID> {

    List<AgentCredential> findByAgentIdOrderByCreatedAtDesc(UUID agentId);

    Optional<AgentCredential> findByIdAndAgentId(UUID id, UUID agentId);

    Optional<AgentCredential> findFirstByAgentIdAndActiveTrueOrderByCreatedAtDesc(UUID agentId);

    /** Whether the agent currently has an active credential — powers the "credential configured" readiness signal. */
    boolean existsByAgentIdAndActiveTrue(UUID agentId);

    /** The subset of the given agent ids that have an active credential — one query for list views (no N+1). */
    @Query("select distinct c.agentId from AgentCredential c where c.active = true and c.agentId in :agentIds")
    List<UUID> findAgentIdsWithActiveCredential(@Param("agentIds") Collection<UUID> agentIds);
}
