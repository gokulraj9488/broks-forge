package com.broksforge.modules.dataset.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(name = "DatasetStatsResponse", description = "Computed statistics for a dataset version")
public record DatasetStatsResponse(
        UUID datasetId,
        UUID datasetVersionId,
        int versionNumber,
        long itemCount,
        long itemsWithExpectedOutput,
        double expectedOutputCoverage,
        Double avgInputLength,
        Double avgExpectedOutputLength,
        List<String> columns
) {
}
