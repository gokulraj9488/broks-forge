package com.broksforge.modules.apikey.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(name = "CreateApiKeyRequest")
public record CreateApiKeyRequest(

        @Schema(example = "CI pipeline key")
        @NotBlank(message = "Name is required")
        @Size(min = 1, max = 120)
        String name,

        @Schema(description = "Optional expiry in days from now; omit for a non-expiring key", example = "90")
        @Positive(message = "Expiry must be a positive number of days")
        @Max(value = 3650, message = "Expiry may not exceed 10 years")
        Integer expiresInDays
) {
}
