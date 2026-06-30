package com.broksforge.modules.organization.web;

import com.broksforge.common.web.PageResponse;
import com.broksforge.modules.organization.service.OrganizationService;
import com.broksforge.modules.organization.web.dto.AddOrganizationMemberRequest;
import com.broksforge.modules.organization.web.dto.OrganizationMemberResponse;
import com.broksforge.modules.organization.web.dto.UpdateMemberRoleRequest;
import com.broksforge.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/members")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Organization Members", description = "Manage members and their roles")
public class OrganizationMemberController {

    private final OrganizationService organizationService;

    public OrganizationMemberController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping
    @Operation(summary = "List members", description = "Any member of the organization may list members.")
    public ResponseEntity<PageResponse<OrganizationMemberResponse>> list(
            @PathVariable UUID organizationId,
            @ParameterObject @PageableDefault(size = 20, sort = "joinedAt", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return ResponseEntity.ok(
                organizationService.listMembers(SecurityUtils.requireCurrentUserId(), organizationId, pageable));
    }

    @PostMapping
    @Operation(summary = "Add a member", description = "Adds an existing user by email. Requires ADMIN or higher.")
    public ResponseEntity<OrganizationMemberResponse> add(@PathVariable UUID organizationId,
                                                          @Valid @RequestBody AddOrganizationMemberRequest request) {
        OrganizationMemberResponse response =
                organizationService.addMember(SecurityUtils.requireCurrentUserId(), organizationId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{userId}")
    @Operation(summary = "Change a member's role", description = "Requires ADMIN or higher.")
    public ResponseEntity<OrganizationMemberResponse> updateRole(@PathVariable UUID organizationId,
                                                                 @PathVariable UUID userId,
                                                                 @Valid @RequestBody UpdateMemberRoleRequest request) {
        return ResponseEntity.ok(organizationService.updateMemberRole(
                SecurityUtils.requireCurrentUserId(), organizationId, userId, request));
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "Remove a member",
            description = "Removes a member, or leaves the organization if removing yourself.")
    public ResponseEntity<Void> remove(@PathVariable UUID organizationId, @PathVariable UUID userId) {
        organizationService.removeMember(SecurityUtils.requireCurrentUserId(), organizationId, userId);
        return ResponseEntity.noContent().build();
    }
}
