package com.broksforge.modules.dataset.web.dto;

import com.broksforge.modules.dataset.domain.DatasetStatus;
import com.broksforge.modules.dataset.domain.DatasetVisibility;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Partial update of dataset metadata. Null fields are left unchanged; a non-null
 * {@code tags} replaces the existing set.
 */
@Schema(name = "UpdateDatasetRequest", description = "Update dataset metadata")
public record UpdateDatasetRequest(
        @Size(max = 120) String name,
        @Size(max = 1000) String description,
        DatasetVisibility visibility,
        DatasetStatus status,
        @Size(max = 32) Set<@Size(max = 40) String> tags
) {
}
