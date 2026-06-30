package com.broksforge.modules.organization.web;

import com.broksforge.common.web.PageResponse;
import com.broksforge.modules.organization.service.OrganizationService;
import com.broksforge.modules.organization.web.dto.CreateOrganizationRequest;
import com.broksforge.modules.organization.web.dto.OrganizationResponse;
import com.broksforge.modules.organization.web.dto.UpdateOrganizationRequest;
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
@RequestMapping("/api/v1/organizations")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Organizations", description = "Create and manage organizations")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @PostMapping
    @Operation(summary = "Create an organization",
            description = "Creates a new organization; the caller becomes its owner.")
    public ResponseEntity<OrganizationResponse> create(@Valid @RequestBody CreateOrganizationRequest request) {
        OrganizationResponse response = organizationService.create(SecurityUtils.requireCurrentUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List my organizations",
            description = "Lists the organizations the authenticated user belongs to.")
    public ResponseEntity<PageResponse<OrganizationResponse>> list(
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(organizationService.listForUser(SecurityUtils.requireCurrentUserId(), pageable));
    }

    @GetMapping("/{organizationId}")
    @Operation(summary = "Get an organization")
    public ResponseEntity<OrganizationResponse> get(@PathVariable UUID organizationId) {
        return ResponseEntity.ok(organizationService.get(SecurityUtils.requireCurrentUserId(), organizationId));
    }

    @PatchMapping("/{organizationId}")
    @Operation(summary = "Update an organization", description = "Requires the ADMIN role or higher.")
    public ResponseEntity<OrganizationResponse> update(@PathVariable UUID organizationId,
                                                       @Valid @RequestBody UpdateOrganizationRequest request) {
        return ResponseEntity.ok(
                organizationService.update(SecurityUtils.requireCurrentUserId(), organizationId, request));
    }

    @DeleteMapping("/{organizationId}")
    @Operation(summary = "Delete an organization", description = "Soft-deletes the organization. Owner only.")
    public ResponseEntity<Void> delete(@PathVariable UUID organizationId) {
        organizationService.delete(SecurityUtils.requireCurrentUserId(), organizationId);
        return ResponseEntity.noContent().build();
    }
}
