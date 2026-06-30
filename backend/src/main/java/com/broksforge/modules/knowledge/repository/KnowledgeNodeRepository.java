package com.broksforge.modules.knowledge.repository;

import com.broksforge.modules.knowledge.domain.KnowledgeNode;
import com.broksforge.modules.knowledge.domain.KnowledgeNodeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KnowledgeNodeRepository extends JpaRepository<KnowledgeNode, UUID> {

    Optional<KnowledgeNode> findByNodeKey(String nodeKey);

    List<KnowledgeNode> findByNodeTypeOrderByTitleAsc(KnowledgeNodeType nodeType);

    List<KnowledgeNode> findByCategoryIgnoreCaseOrderByTitleAsc(String category);

    List<KnowledgeNode> findAllByOrderByCategoryAscTitleAsc();

    /**
     * Increments the observation counter for a pattern — the learning seam. A single
     * atomic UPDATE so concurrent observations do not lose increments.
     */
    @Modifying
    @Query("UPDATE KnowledgeNode n SET n.occurrenceCount = n.occurrenceCount + 1 WHERE n.nodeKey = :nodeKey")
    int incrementOccurrence(@Param("nodeKey") String nodeKey);
}
