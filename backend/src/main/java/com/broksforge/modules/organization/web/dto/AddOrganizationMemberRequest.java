package com.broksforge.modules.organization.web.dto;

import com.broksforge.modules.organization.domain.OrganizationRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(name = "AddOrganizationMemberRequest")
public record AddOrganizationMemberRequest(

        @Schema(description = "Email of an existing platform user to add", example = "grace@example.com")
        @NotBlank(message = "Email is required")
        @Email(message = "Must be a well-formed email address")
        String email,

        @Schema(description = "Role to grant. OWNER cannot be granted here.", example = "MEMBER")
        @NotNull(message = "Role is required")
        OrganizationRole role
) {
}
