package com.broksforge.modules.user.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Mutable profile fields. Email changes are intentionally out of scope for this
 * endpoint (they require re-verification and are handled separately).
 */
@Schema(name = "UpdateProfileRequest")
public record UpdateProfileRequest(

        @Schema(example = "Ada")
        @Size(max = 100, message = "First name must be at most 100 characters")
        String firstName,

        @Schema(example = "Lovelace")
        @Size(max = 100, message = "Last name must be at most 100 characters")
        String lastName
) {
}
