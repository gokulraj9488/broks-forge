package com.broksforge.modules.apikey.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Returned once, at creation. Contains the full plaintext key, which is shown
 * only this one time and can never be retrieved again.
 */
@Schema(name = "CreatedApiKeyResponse")
public record CreatedApiKeyResponse(

        @Schema(description = "The full secret key. Copy it now; it cannot be retrieved later.",
                example = "bf_a1b2c3d4e5f6.s3cr3t-r4nd0m-v4lu3")
        String plaintextKey,

        @Schema(description = "Metadata about the created key")
        ApiKeyResponse apiKey
) {
}
