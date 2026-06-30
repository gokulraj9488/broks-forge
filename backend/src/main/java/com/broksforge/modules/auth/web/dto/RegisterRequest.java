package com.broksforge.modules.auth.web.dto;

import com.broksforge.common.validation.StrongPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "RegisterRequest")
public record RegisterRequest(

        @Schema(example = "ada@example.com")
        @NotBlank(message = "Email is required")
        @Email(message = "Must be a well-formed email address")
        @Size(max = 254)
        String email,

        @Schema(example = "Sup3rSecret!")
        @StrongPassword
        String password,

        @Schema(example = "Ada")
        @Size(max = 100)
        String firstName,

        @Schema(example = "Lovelace")
        @Size(max = 100)
        String lastName
) {
}
