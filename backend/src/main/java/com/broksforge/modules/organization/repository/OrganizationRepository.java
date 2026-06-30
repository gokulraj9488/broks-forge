package com.broksforge.modules.organization.repository;

import com.broksforge.modules.organization.domain.Organization;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    Optional<Organization> findByIdAndDeletedFalse(UUID id);

    boolean existsBySlugIgnoreCaseAndDeletedFalse(String slug);

    /**
     * Lists the (non-deleted) organizations the given user is a member of.
     */
    @Query(value = """
            select o from Organization o, OrganizationMember m
            where m.organizationId = o.id
              and m.userId = :userId
              and o.deleted = false
            """,
            countQuery = """
            select count(o) from Organization o, OrganizationMember m
            where m.organizationId = o.id
              and m.userId = :userId
              and o.deleted = false
            """)
    Page<Organization> findAllForMember(@Param("userId") UUID userId, Pageable pageable);
}
