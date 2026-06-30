package com.broksforge.modules.auth.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "LoginRequest")
public record LoginRequest(

        @Schema(example = "ada@example.com")
        @NotBlank(message = "Email is required")
        @Email(message = "Must be a well-formed email address")
        String email,

        @Schema(example = "Sup3rSecret!")
        @NotBlank(message = "Password is required")
        String password
) {
}
