package com.broksforge.modules.dataset.web.dto;

import com.broksforge.modules.dataset.domain.DatasetStatus;
import com.broksforge.modules.dataset.domain.DatasetVisibility;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(name = "DatasetSummaryResponse", description = "Compact dataset listing row")
public record DatasetSummaryResponse(
        UUID id,
        String name,
        String slug,
        DatasetVisibility visibility,
        DatasetStatus status,
        List<String> tags,
        int latestVersionNumber,
        int currentItemCount,
        Instant updatedAt
) {
}
