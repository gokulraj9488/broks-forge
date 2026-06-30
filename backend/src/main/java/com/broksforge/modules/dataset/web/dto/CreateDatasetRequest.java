package com.broksforge.modules.dataset.web.dto;

import com.broksforge.modules.dataset.domain.DatasetVisibility;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Request to register a dataset container. Server-controlled fields (owner,
 * organization, project, status, version pointers) are never accepted here —
 * preventing mass assignment.
 */
@Schema(name = "CreateDatasetRequest", description = "Register a new dataset")
public record CreateDatasetRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 64) String slug,
        @Size(max = 1000) String description,
        DatasetVisibility visibility,
        @Size(max = 32) Set<@Size(max = 40) String> tags
) {
    public DatasetVisibility visibilityOrDefault() {
        return visibility != null ? visibility : DatasetVisibility.PRIVATE;
    }
}
