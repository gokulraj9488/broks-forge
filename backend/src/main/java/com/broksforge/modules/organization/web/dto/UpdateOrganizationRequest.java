package com.broksforge.modules.organization.web.dto;

import com.broksforge.modules.organization.domain.OrganizationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Partial update. Null fields are left unchanged. The slug is immutable once
 * created (it is a stable identifier).
 */
@Schema(name = "UpdateOrganizationRequest")
public record UpdateOrganizationRequest(

        @Schema(example = "Acme AI Labs")
        @Size(min = 2, max = 120)
        String name,

        @Schema(example = "Updated description")
        @Size(max = 1000)
        String description,

        @Schema(description = "Lifecycle status")
        OrganizationStatus status
) {
}
