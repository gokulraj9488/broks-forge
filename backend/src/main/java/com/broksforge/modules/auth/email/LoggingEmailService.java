package com.broksforge.modules.auth.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Development / CI e-mail transport that records messages in the application log
 * instead of dispatching them: zero configuration, no SMTP, no provider account,
 * fully offline. It logs the verification and password-reset links (clickable URLs)
 * to the backend console so the flow is fully exercisable without any e-mail
 * infrastructure.
 *
 * <p>Active on every profile <em>except</em> {@code prod} ({@code @Profile("!prod")}).
 * The production profile activates {@link SmtpEmailService} instead — exactly one
 * {@link EmailService} bean exists per profile, and
 * {@link com.broksforge.modules.auth.service.AuthService} depends only on the
 * abstraction and never knows which transport is active (see ADR 0016).</p>
 *
 * <p>The {@code docker} profile deliberately enables Spring Boot's native ECS JSON
 * structured console logging (see {@code application-docker.yml}) so the stack
 * demonstrates production-grade log aggregation locally. That is correct behaviour
 * for real application logs, but it is hostile to a link a human needs to click:
 * the whole message is flattened into one JSON string, escaping every {@code /} to
 * {@code \/} and collapsing newlines. Rather than weaken structured logging (which
 * would remove a deliberately demonstrated capability) this class also writes the
 * bare link straight to {@link System#out}, bypassing Logback/the active encoder
 * entirely, so local development always has one clean, unescaped, clickable URL —
 * regardless of which console log format is active. This class never runs in
 * {@code prod} ({@code @Profile("!prod")}), so production output is unaffected.</p>
 */
@Slf4j
@Service
@Profile("!prod")
public class LoggingEmailService implements EmailService {

    @Override
    public void sendEmailVerification(String toEmail, String recipientName, String verificationLink) {
        log.info("""

                ===================== EMAIL (verification) =====================
                To:      {} <{}>
                Subject: Verify your Brok's Forge account
                ----------------------------------------------------------------
                Welcome to Brok's Forge! Confirm your e-mail address:
                {}
                This link expires in 24 hours.
                ================================================================
                """, recipientName, toEmail, verificationLink);
        printClickableLink("Verify e-mail", toEmail, verificationLink);
    }

    @Override
    public void sendPasswordReset(String toEmail, String recipientName, String resetLink) {
        log.info("""

                ===================== EMAIL (password reset) ===================
                To:      {} <{}>
                Subject: Reset your Brok's Forge password
                ----------------------------------------------------------------
                We received a request to reset your password. Use this link:
                {}
                This link expires in 1 hour. If you did not request this, ignore
                this message.
                ================================================================
                """, recipientName, toEmail, resetLink);
        printClickableLink("Reset password", toEmail, resetLink);
    }

    @Override
    public void sendPasswordChangeVerification(String toEmail, String recipientName, String confirmLink) {
        log.info("""

                ===================== EMAIL (password change) ==================
                To:      {} <{}>
                Subject: Confirm your Brok's Forge password change
                ----------------------------------------------------------------
                We received a request to change your password. Confirm it and
                choose your new password using this link:
                {}
                This link expires in 15 minutes. If you did not request this,
                ignore this message.
                ================================================================
                """, recipientName, toEmail, confirmLink);
        printClickableLink("Confirm password change", toEmail, confirmLink);
    }

    @Override
    public void sendPasswordChangeOtp(String toEmail, String recipientName, String code, int expiryMinutes) {
        log.info("""

                ===================== EMAIL (password change OTP) ==============
                To:      {} <{}>
                Subject: Your Brok's Forge password-change code
                ----------------------------------------------------------------
                We received a request to change your password. Enter this code:
                {}
                This code expires in {} minutes. If you did not request this,
                ignore this message.
                ================================================================
                """, recipientName, toEmail, code, expiryMinutes);
        System.out.printf(">>> [dev-mail] Password-change code for %s: %s%n", toEmail, code);
    }

    @Override
    public void sendPasswordChangedNotification(String toEmail, String recipientName) {
        log.info("""

                ===================== EMAIL (password changed) =================
                To:      {} <{}>
                Subject: Your Brok's Forge password was changed
                ----------------------------------------------------------------
                This is a confirmation that your password was just changed. If
                this wasn't you, contact support immediately.
                ================================================================
                """, recipientName, toEmail);
    }

    /**
     * Writes a plain, unescaped copy of a clickable link to stdout, independent of
     * the active Logback console encoder (plain pattern or ECS/Logstash JSON).
     */
    private void printClickableLink(String action, String toEmail, String link) {
        System.out.printf(">>> [dev-mail] %s for %s: %s%n", action, toEmail, link);
    }
}
