package com.broksforge.modules.organization.web.dto;

import com.broksforge.modules.organization.domain.OrganizationRole;
import com.broksforge.modules.organization.domain.OrganizationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "OrganizationResponse")
public record OrganizationResponse(
        UUID id,
        String name,
        String slug,
        String description,
        OrganizationStatus status,
        UUID ownerId,
        @Schema(description = "The requesting user's role in this organization")
        OrganizationRole currentUserRole,
        long memberCount,
        Instant createdAt,
        Instant updatedAt
) {
}
