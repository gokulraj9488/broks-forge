package com.broksforge.modules.organization.service;

import com.broksforge.common.exception.ErrorCode;
import com.broksforge.common.exception.ForbiddenException;
import com.broksforge.modules.organization.domain.OrganizationMember;
import com.broksforge.modules.organization.domain.OrganizationRole;
import com.broksforge.modules.organization.repository.OrganizationMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Central enforcement point for organization-scoped RBAC. Reused by the
 * project and API key modules so authorization rules live in exactly one place.
 */
@Service
public class OrganizationAccessService {

    private final OrganizationMemberRepository memberRepository;

    public OrganizationAccessService(OrganizationMemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public Optional<OrganizationMember> findMembership(UUID organizationId, UUID userId) {
        return memberRepository.findByOrganizationIdAndUserId(organizationId, userId);
    }

    @Transactional(readOnly = true)
    public boolean isMember(UUID organizationId, UUID userId) {
        return memberRepository.existsByOrganizationIdAndUserId(organizationId, userId);
    }

    /**
     * @return the caller's membership, or throws 403 if they are not a member
     */
    @Transactional(readOnly = true)
    public OrganizationMember requireMembership(UUID organizationId, UUID userId) {
        return memberRepository.findByOrganizationIdAndUserId(organizationId, userId)
                .orElseThrow(() -> new ForbiddenException(ErrorCode.INSUFFICIENT_PERMISSIONS,
                        "You are not a member of this organization"));
    }

    /**
     * @return the caller's membership, or throws 403 if they lack {@code minRole}
     */
    @Transactional(readOnly = true)
    public OrganizationMember requireRole(UUID organizationId, UUID userId, OrganizationRole minRole) {
        OrganizationMember membership = requireMembership(organizationId, userId);
        if (!membership.getRole().isAtLeast(minRole)) {
            throw new ForbiddenException(ErrorCode.INSUFFICIENT_PERMISSIONS,
                    "This action requires the %s role or higher".formatted(minRole));
        }
        return membership;
    }
}
