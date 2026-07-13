package com.broksforge.modules.agent.repository;

import com.broksforge.modules.agent.domain.Agent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentRepository extends JpaRepository<Agent, UUID>, JpaSpecificationExecutor<Agent> {

    Optional<Agent> findByIdAndProjectIdAndOrganizationIdAndDeletedFalse(UUID id, UUID projectId, UUID organizationId);

    boolean existsByProjectIdAndSlugIgnoreCaseAndDeletedFalse(UUID projectId, String slug);

    int countByProviderIdAndDeletedFalse(UUID providerId);
}
