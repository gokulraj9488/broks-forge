package com.broksforge.modules.platform.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One entry in the unified engineering registry — a read-only, discovery-oriented view of a real domain
 * artifact. It carries only catalog metadata; editing stays in the artifact's own module. {@code entityId}
 * and {@code projectId} let the UI deep-link to the existing management page.
 */
@Schema(name = "RegistryItemResponse", description = "A real engineering artifact in the unified registry")
public record RegistryItemResponse(
        @Schema(description = "Stable registry id, e.g. \"prompt:<uuid>\"") String id,
        @Schema(description = "Artifact kind (lowercase)") String type,
        @Schema(description = "Display name") String name,
        @Schema(description = "Secondary label (type/status)") String subtitle,
        @Schema(description = "Underlying entity id") UUID entityId,
        @Schema(description = "Owning project id, when applicable") UUID projectId,
        @Schema(description = "Owning project name, when applicable") String projectName,
        @Schema(description = "Related provider id, when applicable") UUID providerId,
        @Schema(description = "Tags, where the artifact supports them") List<String> tags,
        @Schema(description = "Creation timestamp") Instant createdAt
) {
}
