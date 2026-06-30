package com.broksforge.modules.user.web;

import com.broksforge.common.exception.ApiError;
import com.broksforge.modules.user.domain.User;
import com.broksforge.modules.user.service.UserService;
import com.broksforge.modules.user.web.dto.UpdateProfileRequest;
import com.broksforge.modules.user.web.dto.UserResponse;
import com.broksforge.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Endpoints for the authenticated user's own profile.
 */
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("isAuthenticated()")
@Tag(name = "User Profile", description = "Manage the authenticated user's profile")
public class UserProfileController {

    private final UserService userService;
    private final UserMapper userMapper;

    public UserProfileController(UserService userService, UserMapper userMapper) {
        this.userService = userService;
        this.userMapper = userMapper;
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user", description = "Returns the profile of the authenticated user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile returned"),
            @ApiResponse(responseCode = "401", description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<UserResponse> getCurrentUser() {
        UUID userId = SecurityUtils.requireCurrentUserId();
        User user = userService.getActiveByIdOrThrow(userId);
        return ResponseEntity.ok(userMapper.toResponse(user));
    }

    @PatchMapping("/me")
    @Operation(summary = "Update current user profile",
            description = "Updates the first and/or last name of the authenticated user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<UserResponse> updateCurrentUser(@Valid @RequestBody UpdateProfileRequest request) {
        UUID userId = SecurityUtils.requireCurrentUserId();
        User updated = userService.updateProfile(userId, request);
        return ResponseEntity.ok(userMapper.toResponse(updated));
    }
}
