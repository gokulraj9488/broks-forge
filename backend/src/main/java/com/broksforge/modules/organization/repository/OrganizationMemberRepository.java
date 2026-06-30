package com.broksforge.modules.organization.repository;

import com.broksforge.modules.organization.domain.OrganizationMember;
import com.broksforge.modules.organization.domain.OrganizationRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, UUID> {

    Optional<OrganizationMember> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    boolean existsByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    Page<OrganizationMember> findByOrganizationId(UUID organizationId, Pageable pageable);

    long countByOrganizationId(UUID organizationId);

    long countByOrganizationIdAndRole(UUID organizationId, OrganizationRole role);
}
