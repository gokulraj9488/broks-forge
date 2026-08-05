package com.broksforge.modules.platform.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** How many registry artifacts of a given kind exist in an organization — powers the type filters. */
@Schema(name = "RegistryTypeCount", description = "Artifact-type count for the registry filters")
public record RegistryTypeCount(
        @Schema(description = "Artifact kind (lowercase)") String type,
        @Schema(description = "Number of artifacts of this kind") long count
) {
}
