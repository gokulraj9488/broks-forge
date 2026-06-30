package com.broksforge.modules.user.web.dto;

import com.broksforge.modules.user.domain.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Public representation of a user. Never exposes the password hash.
 */
@Schema(name = "UserResponse", description = "A platform user")
public record UserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String fullName,
        UserStatus status,
        boolean emailVerified,
        Set<String> roles,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt
) {
}
