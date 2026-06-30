package com.broksforge.modules.organization.service;

import com.broksforge.common.exception.ErrorCode;
import com.broksforge.common.exception.ResourceConflictException;
import com.broksforge.common.exception.ResourceNotFoundException;
import com.broksforge.common.exception.BadRequestException;
import com.broksforge.common.exception.ForbiddenException;
import com.broksforge.common.util.SlugGenerator;
import com.broksforge.common.web.PageResponse;
import com.broksforge.modules.organization.domain.Organization;
import com.broksforge.modules.organization.domain.OrganizationMember;
import com.broksforge.modules.organization.domain.OrganizationRole;
import com.broksforge.modules.organization.repository.OrganizationMemberRepository;
import com.broksforge.modules.organization.repository.OrganizationRepository;
import com.broksforge.modules.organization.web.OrganizationMapper;
import com.broksforge.modules.organization.web.dto.AddOrganizationMemberRequest;
import com.broksforge.modules.organization.web.dto.CreateOrganizationRequest;
import com.broksforge.modules.organization.web.dto.OrganizationMemberResponse;
import com.broksforge.modules.organization.web.dto.OrganizationResponse;
import com.broksforge.modules.organization.web.dto.UpdateMemberRoleRequest;
import com.broksforge.modules.organization.web.dto.UpdateOrganizationRequest;
import com.broksforge.modules.user.domain.User;
import com.broksforge.modules.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application service for organizations and their membership. Authorization is
 * enforced through {@link OrganizationAccessService}; the rule "an organization
 * always has at least one owner" is upheld here.
 */
@Slf4j
@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final OrganizationAccessService accessService;
    private final UserService userService;
    private final OrganizationMapper mapper;

    public OrganizationService(OrganizationRepository organizationRepository,
                               OrganizationMemberRepository memberRepository,
                               OrganizationAccessService accessService,
                               UserService userService,
                               OrganizationMapper mapper) {
        this.organizationRepository = organizationRepository;
        this.memberRepository = memberRepository;
        this.accessService = accessService;
        this.userService = userService;
        this.mapper = mapper;
    }

    // ----------------------------------------------------------------------
    // Organizations
    // ----------------------------------------------------------------------

    @Transactional
    public OrganizationResponse create(UUID actorId, CreateOrganizationRequest request) {
        String slug = resolveSlug(request.slug(), request.name());

        Organization organization = new Organization();
        organization.setName(request.name().trim());
        organization.setSlug(slug);
        organization.setDescription(trimToNull(request.description()));
        organization.setOwnerId(actorId);
        Organization saved = organizationRepository.save(organization);

        OrganizationMember owner = new OrganizationMember();
        owner.setOrganizationId(saved.getId());
        owner.setUserId(actorId);
        owner.setRole(OrganizationRole.OWNER);
        owner.setJoinedAt(Instant.now());
        memberRepository.save(owner);

        log.info("Organization {} ('{}') created by user {}", saved.getId(), slug, actorId);
        return mapper.toResponse(saved, OrganizationRole.OWNER, 1);
    }

    @Transactional(readOnly = true)
    public OrganizationResponse get(UUID actorId, UUID organizationId) {
        OrganizationMember membership = accessService.requireMembership(organizationId, actorId);
        Organization organization = getOrThrow(organizationId);
        long memberCount = memberRepository.countByOrganizationId(organizationId);
        return mapper.toResponse(organization, membership.getRole(), memberCount);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrganizationResponse> listForUser(UUID actorId, Pageable pageable) {
        Page<Organization> page = organizationRepository.findAllForMember(actorId, pageable);
        return PageResponse.from(page.map(org -> {
            OrganizationRole role = accessService.findMembership(org.getId(), actorId)
                    .map(OrganizationMember::getRole)
                    .orElse(OrganizationRole.MEMBER);
            long memberCount = memberRepository.countByOrganizationId(org.getId());
            return mapper.toResponse(org, role, memberCount);
        }));
    }

    @Transactional
    public OrganizationResponse update(UUID actorId, UUID organizationId, UpdateOrganizationRequest request) {
        OrganizationMember membership = accessService.requireRole(organizationId, actorId, OrganizationRole.ADMIN);
        Organization organization = getOrThrow(organizationId);

        if (StringUtils.hasText(request.name())) {
            organization.setName(request.name().trim());
        }
        if (request.description() != null) {
            organization.setDescription(trimToNull(request.description()));
        }
        if (request.status() != null) {
            organization.setStatus(request.status());
        }
        long memberCount = memberRepository.countByOrganizationId(organizationId);
        return mapper.toResponse(organization, membership.getRole(), memberCount);
    }

    @Transactional
    public void delete(UUID actorId, UUID organizationId) {
        accessService.requireRole(organizationId, actorId, OrganizationRole.OWNER);
        Organization organization = getOrThrow(organizationId);
        organization.softDelete(actorId);
        log.info("Organization {} soft-deleted by user {}", organizationId, actorId);
    }

    // ----------------------------------------------------------------------
    // Members
    // ----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<OrganizationMemberResponse> listMembers(UUID actorId, UUID organizationId, Pageable pageable) {
        accessService.requireMembership(organizationId, actorId);
        getOrThrow(organizationId);

        Page<OrganizationMember> page = memberRepository.findByOrganizationId(organizationId, pageable);
        Set<UUID> userIds = page.getContent().stream()
                .map(OrganizationMember::getUserId)
                .collect(Collectors.toSet());
        Map<UUID, User> users = userService.getUsersByIds(userIds);

        return PageResponse.from(page.map(member -> toMemberResponse(member, users)));
    }

    @Transactional
    public OrganizationMemberResponse addMember(UUID actorId, UUID organizationId,
                                                AddOrganizationMemberRequest request) {
        accessService.requireRole(organizationId, actorId, OrganizationRole.ADMIN);
        getOrThrow(organizationId);

        if (request.role() == OrganizationRole.OWNER) {
            throw new BadRequestException("The OWNER role cannot be granted by adding a member");
        }

        User user = userService.findActiveByEmail(request.email())
                .orElseThrow(() -> new ResourceNotFoundException("No user found with email " + request.email()));

        if (memberRepository.existsByOrganizationIdAndUserId(organizationId, user.getId())) {
            throw new ResourceConflictException(ErrorCode.ALREADY_MEMBER,
                    "User is already a member of this organization");
        }

        OrganizationMember member = new OrganizationMember();
        member.setOrganizationId(organizationId);
        member.setUserId(user.getId());
        member.setRole(request.role());
        member.setJoinedAt(Instant.now());
        OrganizationMember saved = memberRepository.save(member);

        log.info("User {} added to organization {} as {} by {}", user.getId(), organizationId, request.role(), actorId);
        return mapper.toMemberResponse(saved, user.getEmail(), user.fullName());
    }

    @Transactional
    public OrganizationMemberResponse updateMemberRole(UUID actorId, UUID organizationId, UUID targetUserId,
                                                       UpdateMemberRoleRequest request) {
        OrganizationMember caller = accessService.requireRole(organizationId, actorId, OrganizationRole.ADMIN);
        OrganizationMember target = memberRepository.findByOrganizationIdAndUserId(organizationId, targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found in this organization"));

        if (request.role() == OrganizationRole.OWNER) {
            throw new BadRequestException("Use ownership transfer to grant the OWNER role");
        }
        // Only an owner may change another owner's role, and never the last owner.
        if (target.getRole() == OrganizationRole.OWNER) {
            if (caller.getRole() != OrganizationRole.OWNER) {
                throw new ForbiddenException("Only an owner can change an owner's role");
            }
            ensureNotLastOwner(organizationId);
        }

        target.setRole(request.role());
        User user = userService.getUsersByIds(Set.of(targetUserId)).get(targetUserId);
        log.info("Member {} role changed to {} in organization {} by {}",
                targetUserId, request.role(), organizationId, actorId);
        return mapper.toMemberResponse(target,
                user != null ? user.getEmail() : null,
                user != null ? user.fullName() : null);
    }

    @Transactional
    public void removeMember(UUID actorId, UUID organizationId, UUID targetUserId) {
        boolean removingSelf = actorId.equals(targetUserId);
        if (!removingSelf) {
            accessService.requireRole(organizationId, actorId, OrganizationRole.ADMIN);
        } else {
            accessService.requireMembership(organizationId, actorId);
        }

        OrganizationMember target = memberRepository.findByOrganizationIdAndUserId(organizationId, targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found in this organization"));

        if (target.getRole() == OrganizationRole.OWNER) {
            ensureNotLastOwner(organizationId);
        }

        memberRepository.delete(target);
        log.info("Member {} removed from organization {} by {}", targetUserId, organizationId, actorId);
    }

    // ----------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------

    private OrganizationMemberResponse toMemberResponse(OrganizationMember member, Map<UUID, User> users) {
        User user = users.get(member.getUserId());
        return mapper.toMemberResponse(member,
                user != null ? user.getEmail() : null,
                user != null ? user.fullName() : null);
    }

    private void ensureNotLastOwner(UUID organizationId) {
        long owners = memberRepository.countByOrganizationIdAndRole(organizationId, OrganizationRole.OWNER);
        if (owners <= 1) {
            throw new ResourceConflictException("An organization must always have at least one owner");
        }
    }

    private String resolveSlug(String requestedSlug, String name) {
        if (StringUtils.hasText(requestedSlug)) {
            String slug = requestedSlug.trim().toLowerCase();
            if (organizationRepository.existsBySlugIgnoreCaseAndDeletedFalse(slug)) {
                throw new ResourceConflictException(ErrorCode.SLUG_ALREADY_EXISTS, "Slug is already taken");
            }
            return slug;
        }
        return SlugGenerator.uniqueSlug(name, organizationRepository::existsBySlugIgnoreCaseAndDeletedFalse);
    }

    private Organization getOrThrow(UUID organizationId) {
        return organizationRepository.findByIdAndDeletedFalse(organizationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Organization", organizationId));
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
