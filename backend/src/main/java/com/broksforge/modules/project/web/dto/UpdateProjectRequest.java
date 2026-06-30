package com.broksforge.modules.project.web.dto;

import com.broksforge.modules.project.domain.ProjectStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Partial update. Null fields are left unchanged.
 */
@Schema(name = "UpdateProjectRequest")
public record UpdateProjectRequest(

        @Schema(example = "Customer Support Agent v2")
        @Size(min = 2, max = 120)
        String name,

        @Schema(example = "Updated description")
        @Size(max = 1000)
        String description,

        @Schema(description = "Lifecycle status")
        ProjectStatus status
) {
}
