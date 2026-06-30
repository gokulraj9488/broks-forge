package com.broksforge.modules.agent.repository;

import com.broksforge.modules.agent.domain.AgentHealthCheck;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface AgentHealthCheckRepository extends JpaRepository<AgentHealthCheck, UUID> {

    Page<AgentHealthCheck> findByAgentIdOrderByCheckedAtDesc(UUID agentId, Pageable pageable);

    long countByAgentIdAndCheckedAtAfter(UUID agentId, Instant after);

    long countByAgentIdAndSuccessTrueAndCheckedAtAfter(UUID agentId, Instant after);
}
