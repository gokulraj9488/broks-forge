package com.broksforge.modules.agent.repository;

import com.broksforge.modules.agent.domain.AgentTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AgentTagRepository extends JpaRepository<AgentTag, UUID> {

    List<AgentTag> findByAgentIdOrderByLabelAsc(UUID agentId);

    List<AgentTag> findByAgentIdInOrderByLabelAsc(List<UUID> agentIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    void deleteByAgentId(UUID agentId);
}
