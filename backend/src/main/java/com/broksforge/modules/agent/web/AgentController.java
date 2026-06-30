package com.broksforge.modules.agent.web;

import com.broksforge.common.web.PageResponse;
import com.broksforge.modules.agent.service.AgentService;
import com.broksforge.modules.agent.web.dto.AgentFilter;
import com.broksforge.modules.agent.web.dto.AgentResponse;
import com.broksforge.modules.agent.web.dto.AgentSummaryResponse;
import com.broksforge.modules.agent.web.dto.RegisterAgentRequest;
import com.broksforge.modules.agent.web.dto.UpdateAgentRequest;
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

/**
 * Agent registry CRUD, search and lifecycle. Versions, credentials and health
 * live in dedicated controllers under the same path to keep each focused.
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/projects/{projectId}/agents")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Agents", description = "Register, search and manage AI agents")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping
    @Operation(summary = "Register an agent", description = "Registers a new agent. Requires organization membership.")
    public ResponseEntity<AgentResponse> register(@PathVariable UUID organizationId,
                                                  @PathVariable UUID projectId,
                                                  @Valid @RequestBody RegisterAgentRequest request) {
        AgentResponse response =
                agentService.register(SecurityUtils.requireCurrentUserId(), organizationId, projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List & search agents",
            description = "Paginated list with free-text search and filters (framework, language, visibility, "
                    + "status, health, tag).")
    public ResponseEntity<PageResponse<AgentSummaryResponse>> list(
            @PathVariable UUID organizationId,
            @PathVariable UUID projectId,
            @ParameterObject AgentFilter filter,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(
                agentService.search(SecurityUtils.requireCurrentUserId(), organizationId, projectId, filter, pageable));
    }

    @GetMapping("/{agentId}")
    @Operation(summary = "Get an agent")
    public ResponseEntity<AgentResponse> get(@PathVariable UUID organizationId,
                                             @PathVariable UUID projectId,
                                             @PathVariable UUID agentId) {
        return ResponseEntity.ok(
                agentService.get(SecurityUtils.requireCurrentUserId(), organizationId, projectId, agentId));
    }

    @PatchMapping("/{agentId}")
    @Operation(summary = "Update an agent", description = "Requires organization membership.")
    public ResponseEntity<AgentResponse> update(@PathVariable UUID organizationId,
                                                @PathVariable UUID projectId,
                                                @PathVariable UUID agentId,
                                                @Valid @RequestBody UpdateAgentRequest request) {
        return ResponseEntity.ok(
                agentService.update(SecurityUtils.requireCurrentUserId(), organizationId, projectId, agentId, request));
    }

    @DeleteMapping("/{agentId}")
    @Operation(summary = "Delete an agent", description = "Soft-deletes the agent. Requires ADMIN or higher.")
    public ResponseEntity<Void> delete(@PathVariable UUID organizationId,
                                       @PathVariable UUID projectId,
                                       @PathVariable UUID agentId) {
        agentService.delete(SecurityUtils.requireCurrentUserId(), organizationId, projectId, agentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{agentId}/archive")
    @Operation(summary = "Archive an agent")
    public ResponseEntity<AgentResponse> archive(@PathVariable UUID organizationId,
                                                 @PathVariable UUID projectId,
                                                 @PathVariable UUID agentId) {
        return ResponseEntity.ok(
                agentService.archive(SecurityUtils.requireCurrentUserId(), organizationId, projectId, agentId));
    }

    @PostMapping("/{agentId}/unarchive")
    @Operation(summary = "Unarchive an agent")
    public ResponseEntity<AgentResponse> unarchive(@PathVariable UUID organizationId,
                                                   @PathVariable UUID projectId,
                                                   @PathVariable UUID agentId) {
        return ResponseEntity.ok(
                agentService.unarchive(SecurityUtils.requireCurrentUserId(), organizationId, projectId, agentId));
    }
}
