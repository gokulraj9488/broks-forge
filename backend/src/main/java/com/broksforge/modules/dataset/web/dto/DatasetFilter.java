package com.broksforge.modules.dataset.web.dto;

import com.broksforge.modules.dataset.domain.DatasetStatus;
import com.broksforge.modules.dataset.domain.DatasetVisibility;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Query parameters for dataset search. All fields are optional and combine with AND.
 */
@Schema(name = "DatasetFilter", description = "Dataset search and filter parameters")
public record DatasetFilter(
        @Schema(description = "Free-text match over name, slug and description") String q,
        DatasetStatus status,
        DatasetVisibility visibility,
        @Schema(description = "Exact tag match") String tag
) {
}
