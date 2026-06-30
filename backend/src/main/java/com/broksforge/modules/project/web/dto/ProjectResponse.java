package com.broksforge.modules.project.web.dto;

import com.broksforge.modules.project.domain.ProjectStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "ProjectResponse")
public record ProjectResponse(
        UUID id,
        UUID organizationId,
        String name,
        String slug,
        String description,
        ProjectStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
