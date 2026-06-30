package com.broksforge.modules.agent.web;

import com.broksforge.common.web.PageResponse;
import com.broksforge.modules.agent.service.AgentVersionService;
import com.broksforge.modules.agent.web.dto.AgentVersionResponse;
import com.broksforge.modules.agent.web.dto.RegisterAgentVersionRequest;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Agent version (deployment) lifecycle: register, list, activate and rollback.
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/projects/{projectId}/agents/{agentId}/versions")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Agent Versions", description = "Register and activate agent deployments")
public class AgentVersionController {

    private final AgentVersionService versionService;

    public AgentVersionController(AgentVersionService versionService) {
        this.versionService = versionService;
    }

    @PostMapping
    @Operation(summary = "Register a version", description = "Records a new deployment. Requires organization membership.")
    public ResponseEntity<AgentVersionResponse> register(@PathVariable UUID organizationId,
                                                         @PathVariable UUID projectId,
                                                         @PathVariable UUID agentId,
                                                         @Valid @RequestBody RegisterAgentVersionRequest request) {
        AgentVersionResponse response = versionService.register(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, agentId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List versions")
    public ResponseEntity<PageResponse<AgentVersionResponse>> list(
            @PathVariable UUID organizationId,
            @PathVariable UUID projectId,
            @PathVariable UUID agentId,
            @ParameterObject @PageableDefault(size = 20, sort = "sequence", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(versionService.list(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, agentId, pageable));
    }

    @PostMapping("/{versionId}/activate")
    @Operation(summary = "Activate a version",
            description = "Makes this version the active one, deactivating the previous active version.")
    public ResponseEntity<AgentVersionResponse> activate(@PathVariable UUID organizationId,
                                                         @PathVariable UUID projectId,
                                                         @PathVariable UUID agentId,
                                                         @PathVariable UUID versionId) {
        return ResponseEntity.ok(versionService.activate(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, agentId, versionId));
    }

    @PostMapping("/{versionId}/rollback")
    @Operation(summary = "Roll back to a version",
            description = "Activates a previous, rollback-ready version.")
    public ResponseEntity<AgentVersionResponse> rollback(@PathVariable UUID organizationId,
                                                         @PathVariable UUID projectId,
                                                         @PathVariable UUID agentId,
                                                         @PathVariable UUID versionId) {
        return ResponseEntity.ok(versionService.rollback(
                SecurityUtils.requireCurrentUserId(), organizationId, projectId, agentId, versionId));
    }
}
