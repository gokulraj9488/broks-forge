package com.broksforge.modules.knowledge.repository;

import com.broksforge.modules.knowledge.domain.KnowledgeEdge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface KnowledgeEdgeRepository extends JpaRepository<KnowledgeEdge, UUID> {

    List<KnowledgeEdge> findBySourceNodeId(UUID sourceNodeId);

    List<KnowledgeEdge> findByTargetNodeId(UUID targetNodeId);
}
