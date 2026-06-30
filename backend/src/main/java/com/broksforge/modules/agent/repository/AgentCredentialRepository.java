package com.broksforge.modules.agent.repository;

import com.broksforge.modules.agent.domain.AgentCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentCredentialRepository extends JpaRepository<AgentCredential, UUID> {

    List<AgentCredential> findByAgentIdOrderByCreatedAtDesc(UUID agentId);

    Optional<AgentCredential> findByIdAndAgentId(UUID id, UUID agentId);

    Optional<AgentCredential> findFirstByAgentIdAndActiveTrueOrderByCreatedAtDesc(UUID agentId);
}
