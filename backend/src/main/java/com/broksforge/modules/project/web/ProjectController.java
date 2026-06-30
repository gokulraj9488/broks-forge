package com.broksforge.modules.project.web;

import com.broksforge.common.web.PageResponse;
import com.broksforge.modules.project.service.ProjectService;
import com.broksforge.modules.project.web.dto.CreateProjectRequest;
import com.broksforge.modules.project.web.dto.ProjectResponse;
import com.broksforge.modules.project.web.dto.UpdateProjectRequest;
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
@RequestMapping("/api/v1/organizations/{organizationId}/projects")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Projects", description = "Create and manage projects within an organization")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    @Operation(summary = "Create a project", description = "Requires organization membership.")
    public ResponseEntity<ProjectResponse> create(@PathVariable UUID organizationId,
                                                  @Valid @RequestBody CreateProjectRequest request) {
        ProjectResponse response =
                projectService.create(SecurityUtils.requireCurrentUserId(), organizationId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List projects")
    public ResponseEntity<PageResponse<ProjectResponse>> list(
            @PathVariable UUID organizationId,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(projectService.list(SecurityUtils.requireCurrentUserId(), organizationId, pageable));
    }

    @GetMapping("/{projectId}")
    @Operation(summary = "Get a project")
    public ResponseEntity<ProjectResponse> get(@PathVariable UUID organizationId, @PathVariable UUID projectId) {
        return ResponseEntity.ok(projectService.get(SecurityUtils.requireCurrentUserId(), organizationId, projectId));
    }

    @PatchMapping("/{projectId}")
    @Operation(summary = "Update a project", description = "Requires organization membership.")
    public ResponseEntity<ProjectResponse> update(@PathVariable UUID organizationId,
                                                  @PathVariable UUID projectId,
                                                  @Valid @RequestBody UpdateProjectRequest request) {
        return ResponseEntity.ok(
                projectService.update(SecurityUtils.requireCurrentUserId(), organizationId, projectId, request));
    }

    @DeleteMapping("/{projectId}")
    @Operation(summary = "Delete a project", description = "Soft-deletes the project. Requires ADMIN or higher.")
    public ResponseEntity<Void> delete(@PathVariable UUID organizationId, @PathVariable UUID projectId) {
        projectService.delete(SecurityUtils.requireCurrentUserId(), organizationId, projectId);
        return ResponseEntity.noContent().build();
    }
}
