package com.broksforge.modules.apikey.repository;

import com.broksforge.modules.apikey.domain.ApiKey;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    Optional<ApiKey> findByKeyPrefix(String keyPrefix);

    Optional<ApiKey> findByIdAndProjectId(UUID id, UUID projectId);

    Page<ApiKey> findByProjectId(UUID projectId, Pageable pageable);
}
