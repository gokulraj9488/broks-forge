package com.broksforge.modules.auth.web;

import com.broksforge.common.exception.ApiError;
import com.broksforge.common.web.MessageResponse;
import com.broksforge.common.web.RequestUtils;
import com.broksforge.modules.auth.service.AuthService;
import com.broksforge.modules.auth.web.dto.AuthResponse;
import com.broksforge.modules.auth.web.dto.ChangePasswordRequest;
import com.broksforge.modules.auth.web.dto.ForgotPasswordRequest;
import com.broksforge.modules.auth.web.dto.LoginRequest;
import com.broksforge.modules.auth.web.dto.RefreshTokenRequest;
import com.broksforge.modules.auth.web.dto.RegisterRequest;
import com.broksforge.modules.auth.web.dto.ResendVerificationRequest;
import com.broksforge.modules.auth.web.dto.ResetPasswordRequest;
import com.broksforge.modules.auth.web.dto.VerifyEmailRequest;
import com.broksforge.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Authentication endpoints. All routes are public except
 * {@code POST /change-password}, which requires a valid access token.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Registration, login, token lifecycle and account recovery")
public class AuthController {

    private static final String GENERIC_RESET_MESSAGE =
            "If an account exists for that email, a password reset link has been sent.";
    private static final String GENERIC_VERIFY_MESSAGE =
            "If an account exists for that email and is unverified, a verification link has been sent.";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new account",
            description = "Creates a user, sends an e-mail verification link and returns auth tokens.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created"),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Email already registered",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 HttpServletRequest httpRequest) {
        AuthResponse response = authService.register(
                request.email(), request.password(), request.firstName(), request.lastName(),
                RequestUtils.userAgent(httpRequest), RequestUtils.clientIp(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @Operation(summary = "Log in", description = "Exchanges credentials for access and refresh tokens.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authenticated"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest httpRequest) {
        AuthResponse response = authService.login(
                request.email(), request.password(),
                RequestUtils.userAgent(httpRequest), RequestUtils.clientIp(httpRequest));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh tokens",
            description = "Rotates the refresh token and returns a fresh access/refresh pair.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tokens refreshed"),
            @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request,
                                                HttpServletRequest httpRequest) {
        AuthResponse response = authService.refresh(
                request.refreshToken(),
                RequestUtils.userAgent(httpRequest), RequestUtils.clientIp(httpRequest));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "Log out", description = "Revokes the supplied refresh token.")
    public ResponseEntity<MessageResponse> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.ok(MessageResponse.of("Logged out successfully"));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change password", security = @SecurityRequirement(name = "bearerAuth"),
            description = "Changes the authenticated user's password and revokes all existing sessions.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password changed"),
            @ApiResponse(responseCode = "401", description = "Not authenticated or wrong current password",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<MessageResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        UUID userId = SecurityUtils.requireCurrentUserId();
        authService.changePassword(userId, request.currentPassword(), request.newPassword());
        return ResponseEntity.ok(MessageResponse.of("Password changed successfully. Please sign in again."));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a password reset",
            description = "Always returns 200 to avoid revealing whether an email is registered.")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        return ResponseEntity.ok(MessageResponse.of(GENERIC_RESET_MESSAGE));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password using a token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password reset"),
            @ApiResponse(responseCode = "401", description = "Invalid or expired token",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok(MessageResponse.of("Password has been reset. You can now sign in."));
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Verify e-mail address using a token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Email verified"),
            @ApiResponse(responseCode = "401", description = "Invalid or expired token",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<MessageResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request.token());
        return ResponseEntity.ok(MessageResponse.of("Email verified successfully."));
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Resend the e-mail verification link",
            description = "Always returns 200 to avoid revealing account state.")
    public ResponseEntity<MessageResponse> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request.email());
        return ResponseEntity.ok(MessageResponse.of(GENERIC_VERIFY_MESSAGE));
    }
}
