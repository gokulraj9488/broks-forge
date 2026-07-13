package com.broksforge.modules.provider.repository;

import com.broksforge.modules.provider.domain.Provider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProviderRepository extends JpaRepository<Provider, UUID> {

    Optional<Provider> findByIdAndProjectIdAndOrganizationIdAndDeletedFalse(UUID id, UUID projectId,
                                                                            UUID organizationId);

    Page<Provider> findByProjectIdAndDeletedFalse(UUID projectId, Pageable pageable);

    Optional<Provider> findFirstByProjectIdAndTypeAndBaseUrlAndDeletedFalse(UUID projectId,
            com.broksforge.modules.agent.domain.LlmProvider type, String baseUrl);
}
