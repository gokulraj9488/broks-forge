package com.broksforge.modules.dataset.web.dto;

import com.broksforge.modules.dataset.domain.DatasetSourceFormat;
import com.broksforge.modules.dataset.domain.DatasetUploadStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "DatasetUploadResponse", description = "One file-upload attempt and its parser outcome")
public record DatasetUploadResponse(
        UUID id,
        UUID datasetId,
        String filename,
        String contentType,
        DatasetSourceFormat format,
        long sizeBytes,
        String checksum,
        DatasetUploadStatus status,
        Integer rowCount,
        Integer columnCount,
        UUID datasetVersionId,
        String errorMessage,
        Instant createdAt
) {
}
