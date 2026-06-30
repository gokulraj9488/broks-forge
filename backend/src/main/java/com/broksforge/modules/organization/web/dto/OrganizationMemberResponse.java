package com.broksforge.modules.organization.web.dto;

import com.broksforge.modules.organization.domain.OrganizationRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "OrganizationMemberResponse")
public record OrganizationMemberResponse(
        UUID id,
        UUID userId,
        String email,
        String fullName,
        OrganizationRole role,
        Instant joinedAt
) {
}
