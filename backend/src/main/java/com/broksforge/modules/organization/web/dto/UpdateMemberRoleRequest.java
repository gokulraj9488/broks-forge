package com.broksforge.modules.organization.web.dto;

import com.broksforge.modules.organization.domain.OrganizationRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "UpdateMemberRoleRequest")
public record UpdateMemberRoleRequest(

        @Schema(description = "New role for the member", example = "ADMIN")
        @NotNull(message = "Role is required")
        OrganizationRole role
) {
}
