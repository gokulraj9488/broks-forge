package com.broksforge.modules.auth.service;

import com.broksforge.common.exception.ApiException;
import com.broksforge.common.exception.ErrorCode;
import com.broksforge.common.exception.UnauthorizedException;
import com.broksforge.common.ratelimit.RateLimiterService;
import com.broksforge.common.util.SecureTokens;
import com.broksforge.config.properties.AppProperties;
import com.broksforge.config.properties.AuthTokenProperties;
import com.broksforge.modules.auth.domain.EmailVerificationToken;
import com.broksforge.modules.auth.domain.PasswordChangeOtp;
import com.broksforge.modules.auth.domain.PasswordChangeToken;
import com.broksforge.modules.auth.domain.PasswordResetToken;
import com.broksforge.modules.auth.domain.RefreshToken;
import com.broksforge.modules.auth.email.EmailService;
import com.broksforge.modules.auth.repository.EmailVerificationTokenRepository;
import com.broksforge.modules.auth.repository.PasswordChangeOtpRepository;
import com.broksforge.modules.auth.repository.PasswordChangeTokenRepository;
import com.broksforge.modules.auth.repository.PasswordResetTokenRepository;
import com.broksforge.modules.auth.web.dto.AuthResponse;
import com.broksforge.modules.auth.web.dto.PasswordChangeTicketResponse;
import com.broksforge.modules.user.domain.Role;
import com.broksforge.modules.user.domain.User;
import com.broksforge.modules.user.service.CreateUserCommand;
import com.broksforge.modules.user.service.UserService;
import com.broksforge.modules.user.web.UserMapper;
import com.broksforge.security.jwt.JwtService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates the complete authentication lifecycle: registration, login,
 * token refresh/rotation, logout, password change, password reset and e-mail
 * verification.
 */
@Slf4j
@Service
public class AuthService {

    private static final int OTP_DIGITS = 6;
    private static final int OTP_MAX_ATTEMPTS = 5;
    // Per-user throttle on code generation (on top of the per-IP interceptor), so a
    // stolen access token cannot spam a victim's inbox with password-change codes.
    private static final int OTP_GENERATION_LIMIT = 5;
    private static final Duration OTP_GENERATION_WINDOW = Duration.ofMinutes(15);

    private final UserService userService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordChangeTokenRepository passwordChangeTokenRepository;
    private final PasswordChangeOtpRepository passwordChangeOtpRepository;
    private final RateLimiterService rateLimiter;
    private final EmailService emailService;
    private final AuthenticationManager authenticationManager;
    private final UserMapper userMapper;
    private final AuthTokenProperties authTokenProperties;
    private final AppProperties appProperties;

    public AuthService(UserService userService,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService,
                       EmailVerificationTokenRepository emailVerificationTokenRepository,
                       PasswordResetTokenRepository passwordResetTokenRepository,
                       PasswordChangeTokenRepository passwordChangeTokenRepository,
                       PasswordChangeOtpRepository passwordChangeOtpRepository,
                       RateLimiterService rateLimiter,
                       EmailService emailService,
                       AuthenticationManager authenticationManager,
                       UserMapper userMapper,
                       AuthTokenProperties authTokenProperties,
                       AppProperties appProperties) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordChangeTokenRepository = passwordChangeTokenRepository;
        this.passwordChangeOtpRepository = passwordChangeOtpRepository;
        this.rateLimiter = rateLimiter;
        this.emailService = emailService;
        this.authenticationManager = authenticationManager;
        this.userMapper = userMapper;
        this.authTokenProperties = authTokenProperties;
        this.appProperties = appProperties;
    }

    // ----------------------------------------------------------------------
    // Registration
    // ----------------------------------------------------------------------

    @Transactional
    public AuthResponse register(String email, String rawPassword, String firstName, String lastName,
                                 String userAgent, String ipAddress) {
        User user = userService.createUser(new CreateUserCommand(email, rawPassword, firstName, lastName));
        sendEmailVerification(user);
        log.info("Registered new user {}", user.getId());
        return issueAuthResponse(user, userAgent, ipAddress);
    }

    // ----------------------------------------------------------------------
    // Login
    // ----------------------------------------------------------------------

    @Transactional
    public AuthResponse login(String email, String rawPassword, String userAgent, String ipAddress) {
        // Delegates credential and account-status checks to the DaoAuthenticationProvider,
        // which throws BadCredentialsException / DisabledException on failure.
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, rawPassword));

        User user = userService.findActiveByEmail(email)
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password"));
        userService.recordSuccessfulLogin(user.getId());
        log.info("User {} logged in", user.getId());
        return issueAuthResponse(user, userAgent, ipAddress);
    }

    // ----------------------------------------------------------------------
    // Refresh / logout
    // ----------------------------------------------------------------------

    @Transactional
    public AuthResponse refresh(String rawRefreshToken, String userAgent, String ipAddress) {
        RefreshToken current = refreshTokenService.validate(rawRefreshToken);
        User user = userService.getActiveByIdOrThrow(current.getUserId());
        if (!user.isLoginAllowed()) {
            current.revoke();
            throw new UnauthorizedException(ErrorCode.ACCOUNT_DISABLED, "Account is not active");
        }

        RefreshTokenService.IssuedRefreshToken rotated = refreshTokenService.rotate(current, userAgent, ipAddress);
        String accessToken = generateAccessToken(user);
        return AuthResponse.of(
                accessToken,
                jwtService.getAccessTokenExpirationMs() / 1000,
                rotated.token(),
                userMapper.toResponse(user));
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    // ----------------------------------------------------------------------
    // Password change (authenticated, e-mail verified)
    // ----------------------------------------------------------------------

    /**
     * Step 1 of the verified password-change flow: re-authenticates the caller
     * with their current password, then e-mails a one-time confirmation link.
     * Nothing changes until the link is confirmed.
     */
    @Transactional
    public void requestPasswordChange(UUID userId, String currentPassword) {
        User user = userService.getActiveByIdOrThrow(userId);
        if (!userService.checkPassword(user, currentPassword)) {
            throw new UnauthorizedException(ErrorCode.INVALID_CREDENTIALS, "Current password is incorrect");
        }

        passwordChangeTokenRepository.invalidateAllForUser(userId, Instant.now());
        String rawToken = SecureTokens.generateToken();
        PasswordChangeToken token = new PasswordChangeToken();
        token.setUserId(userId);
        token.setTokenHash(SecureTokens.sha256Hex(rawToken));
        token.setExpiresAt(Instant.now().plusMillis(authTokenProperties.passwordChangeExpirationMs()));
        passwordChangeTokenRepository.save(token);

        String link = buildLink("/change-password", rawToken);
        emailService.sendPasswordChangeVerification(user.getEmail(), user.fullName(), link);
        log.info("Password change requested for user {}", userId);
    }

    /**
     * Step 2: consumes the emailed token, applies the new password and revokes
     * every session so the user must sign in again everywhere.
     */
    @Transactional
    public void confirmPasswordChange(String rawToken, String newPassword) {
        PasswordChangeToken token = passwordChangeTokenRepository
                .findByTokenHash(SecureTokens.sha256Hex(rawToken))
                .filter(PasswordChangeToken::isUsable)
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.TOKEN_INVALID,
                        "Password change token is invalid or expired"));

        token.markUsed();
        userService.setPassword(token.getUserId(), newPassword);
        refreshTokenService.revokeAllForUser(token.getUserId());

        User user = userService.getActiveByIdOrThrow(token.getUserId());
        emailService.sendPasswordChangedNotification(user.getEmail(), user.fullName());
        log.info("Password change confirmed for user {}", token.getUserId());
    }

    // ----------------------------------------------------------------------
    // Password change via e-mail OTP (authenticated, 3 steps — see ADR 0017)
    // ----------------------------------------------------------------------

    /**
     * Step 1: re-authenticates the caller with their current password, then
     * e-mails a single-use 6-digit code. Nothing changes yet. Rate-limited
     * per user so a stolen access token cannot flood the victim's inbox.
     */
    @Transactional
    public void requestPasswordChangeOtp(UUID userId, String currentPassword) {
        User user = userService.getActiveByIdOrThrow(userId);
        if (!userService.checkPassword(user, currentPassword)) {
            throw new UnauthorizedException(ErrorCode.INVALID_CREDENTIALS, "Current password is incorrect");
        }
        if (!rateLimiter.tryAcquire("rl:pwd-otp:gen:" + userId, OTP_GENERATION_LIMIT, OTP_GENERATION_WINDOW)) {
            throw new ApiException(ErrorCode.RATE_LIMITED,
                    "Too many password-change codes requested. Please wait a few minutes and try again.");
        }

        passwordChangeOtpRepository.invalidateAllForUser(userId, Instant.now());
        String code = SecureTokens.generateNumericCode(OTP_DIGITS);
        PasswordChangeOtp otp = new PasswordChangeOtp();
        otp.setUserId(userId);
        otp.setCodeHash(SecureTokens.sha256Hex(code));
        otp.setExpiresAt(Instant.now().plusMillis(authTokenProperties.passwordChangeOtpExpirationMs()));
        otp.setMaxAttempts(OTP_MAX_ATTEMPTS);
        passwordChangeOtpRepository.save(otp);

        int expiryMinutes = (int) Math.max(1, authTokenProperties.passwordChangeOtpExpirationMs() / 60_000);
        emailService.sendPasswordChangeOtp(user.getEmail(), user.fullName(), code, expiryMinutes);
        log.info("Password-change OTP issued for user {}", userId);
    }

    /**
     * Step 2: verifies the emailed code and, on success, returns a single-use
     * ticket that authorises the final step. Each wrong code costs an attempt;
     * once attempts are exhausted (or the code expired) the code is burned and a
     * new one must be requested.
     *
     * <p>Uses {@code noRollbackFor = ApiException.class} so the attempt counter
     * (and the burn-on-lockout) persist even though verification failures throw —
     * otherwise the transaction would roll back the increment and defeat the cap.</p>
     */
    @Transactional(noRollbackFor = ApiException.class)
    public PasswordChangeTicketResponse verifyPasswordChangeOtp(UUID userId, String code) {
        PasswordChangeOtp otp = passwordChangeOtpRepository
                .findFirstByUserIdAndConsumedAtIsNullAndVerifiedAtIsNullOrderByCreatedAtDesc(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.OTP_INVALID,
                        "No active code. Request a new password-change code."));

        if (otp.getExpiresAt().isBefore(Instant.now()) || !otp.hasAttemptsRemaining()) {
            otp.markConsumed();
            throw new ApiException(ErrorCode.OTP_LOCKED,
                    "This code is no longer valid. Request a new password-change code.");
        }

        if (!SecureTokens.constantTimeEquals(otp.getCodeHash(), SecureTokens.sha256Hex(code))) {
            otp.recordFailedAttempt();
            if (!otp.hasAttemptsRemaining()) {
                otp.markConsumed();
                throw new ApiException(ErrorCode.OTP_LOCKED,
                        "Too many incorrect attempts. Request a new password-change code.");
            }
            throw new ApiException(ErrorCode.OTP_INVALID, "Incorrect code. Please try again.");
        }

        String ticket = SecureTokens.generateToken();
        Instant ticketExpiry = Instant.now().plusMillis(authTokenProperties.passwordChangeTicketExpirationMs());
        otp.markVerified(SecureTokens.sha256Hex(ticket), ticketExpiry);
        log.info("Password-change OTP verified for user {}", userId);
        return new PasswordChangeTicketResponse(ticket, ticketExpiry);
    }

    /**
     * Step 3: consumes the single-use ticket, applies the new password and
     * revokes every session so the user must sign in again everywhere. The
     * ticket is bound to the caller as defence in depth even though the endpoint
     * is already authenticated.
     */
    @Transactional
    public void completePasswordChange(UUID userId, String ticket, String newPassword) {
        PasswordChangeOtp otp = passwordChangeOtpRepository
                .findByTicketHash(SecureTokens.sha256Hex(ticket))
                .filter(o -> o.getUserId().equals(userId) && o.isTicketUsable())
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.TOKEN_INVALID,
                        "Your verification has expired. Start the password change again."));

        otp.markConsumed();
        userService.setPassword(userId, newPassword);
        refreshTokenService.revokeAllForUser(userId);

        User user = userService.getActiveByIdOrThrow(userId);
        emailService.sendPasswordChangedNotification(user.getEmail(), user.fullName());
        log.info("Password changed via OTP for user {}", userId);
    }

    // ----------------------------------------------------------------------
    // Forgot / reset password
    // ----------------------------------------------------------------------

    @Transactional
    public void forgotPassword(String email) {
        Optional<User> maybeUser = userService.findActiveByEmail(email);
        // Always respond identically to avoid leaking which e-mails are registered.
        maybeUser.ifPresent(user -> {
            passwordResetTokenRepository.invalidateAllForUser(user.getId(), Instant.now());
            String rawToken = SecureTokens.generateToken();
            PasswordResetToken token = new PasswordResetToken();
            token.setUserId(user.getId());
            token.setTokenHash(SecureTokens.sha256Hex(rawToken));
            token.setExpiresAt(Instant.now().plusMillis(authTokenProperties.passwordResetExpirationMs()));
            passwordResetTokenRepository.save(token);

            String link = buildLink("/reset-password", rawToken);
            emailService.sendPasswordReset(user.getEmail(), user.fullName(), link);
        });
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(SecureTokens.sha256Hex(rawToken))
                .filter(PasswordResetToken::isUsable)
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.TOKEN_INVALID,
                        "Password reset token is invalid or expired"));

        token.markUsed();
        userService.setPassword(token.getUserId(), newPassword);
        refreshTokenService.revokeAllForUser(token.getUserId());

        User user = userService.getActiveByIdOrThrow(token.getUserId());
        emailService.sendPasswordChangedNotification(user.getEmail(), user.fullName());
    }

    // ----------------------------------------------------------------------
    // Email verification
    // ----------------------------------------------------------------------

    @Transactional
    public void verifyEmail(String rawToken) {
        EmailVerificationToken token = emailVerificationTokenRepository
                .findByTokenHash(SecureTokens.sha256Hex(rawToken))
                .filter(EmailVerificationToken::isUsable)
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.TOKEN_INVALID,
                        "Verification token is invalid or expired"));

        token.markUsed();
        userService.markEmailVerified(token.getUserId());
    }

    @Transactional
    public void resendVerification(String email) {
        userService.findActiveByEmail(email)
                .filter(user -> !user.isEmailVerified())
                .ifPresent(this::sendEmailVerification);
    }

    // ----------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------

    private void sendEmailVerification(User user) {
        emailVerificationTokenRepository.invalidateAllForUser(user.getId(), Instant.now());
        String rawToken = SecureTokens.generateToken();
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUserId(user.getId());
        token.setTokenHash(SecureTokens.sha256Hex(rawToken));
        token.setExpiresAt(Instant.now().plusMillis(authTokenProperties.emailVerificationExpirationMs()));
        emailVerificationTokenRepository.save(token);

        String link = buildLink("/verify-email", rawToken);
        emailService.sendEmailVerification(user.getEmail(), user.fullName(), link);
    }

    private AuthResponse issueAuthResponse(User user, String userAgent, String ipAddress) {
        String accessToken = generateAccessToken(user);
        RefreshTokenService.IssuedRefreshToken refresh =
                refreshTokenService.issue(user.getId(), userAgent, ipAddress);
        return AuthResponse.of(
                accessToken,
                jwtService.getAccessTokenExpirationMs() / 1000,
                refresh.token(),
                userMapper.toResponse(user));
    }

    private String generateAccessToken(User user) {
        List<String> authorities = user.getRoles().stream().map(Role::authority).toList();
        return jwtService.generateAccessToken(user.getId(), user.getEmail(), authorities);
    }

    private String buildLink(String path, String rawToken) {
        return UriComponentsBuilder.fromUriString(appProperties.publicUrl())
                .path(path)
                .queryParam("token", rawToken)
                .build()
                .toUriString();
    }
}
