package com.broksforge.modules.dataset.web.dto;

import com.broksforge.modules.dataset.domain.DatasetStatus;
import com.broksforge.modules.dataset.domain.DatasetVisibility;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(name = "DatasetResponse", description = "A dataset with its current version pointer")
public record DatasetResponse(
        UUID id,
        UUID organizationId,
        UUID projectId,
        String name,
        String slug,
        String description,
        UUID ownerId,
        DatasetVisibility visibility,
        DatasetStatus status,
        List<String> tags,
        int latestVersionNumber,
        UUID currentVersionId,
        int currentItemCount,
        Instant createdAt,
        Instant updatedAt
) {
}
