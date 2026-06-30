package com.broksforge.modules.dataset.web.dto;

import com.broksforge.modules.dataset.domain.DatasetSourceFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(name = "DatasetVersionResponse", description = "An immutable dataset version snapshot")
public record DatasetVersionResponse(
        UUID id,
        UUID datasetId,
        int versionNumber,
        String description,
        DatasetSourceFormat sourceFormat,
        int itemCount,
        List<String> columns,
        String checksum,
        Instant createdAt
) {
}
