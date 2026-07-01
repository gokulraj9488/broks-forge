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
}
