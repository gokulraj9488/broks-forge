package com.broksforge.modules.auth.email;

import com.broksforge.common.exception.ApiException;
import com.broksforge.common.exception.ErrorCode;
import com.broksforge.config.properties.MailProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * Production e-mail transport. Sends real branded messages over SMTP using Spring's
 * {@link JavaMailSender}, as a {@code multipart/alternative} message carrying both an
 * HTML body and a plain-text fallback (built by {@link EmailContentFactory}).
 *
 * <p>Registered by {@link EmailServiceConfig} only when {@code spring.mail.host} is
 * actually set — SMTP is opt-in by configuration, not by profile, so a {@code prod}
 * deployment without SMTP configured falls back to {@link LoggingEmailService} instead
 * of failing to start. All SMTP connection details and credentials come from
 * {@code spring.mail.*} environment variables — nothing is hardcoded.
 * {@link com.broksforge.modules.auth.service.AuthService} depends only on the
 * {@link EmailService} abstraction and is unaware which transport is active.</p>
 */
@Slf4j
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;
    private final EmailContentFactory content;
    private final MailProperties mailProperties;

    public SmtpEmailService(JavaMailSender mailSender,
                            EmailContentFactory content,
                            MailProperties mailProperties) {
        this.mailSender = mailSender;
        this.content = content;
        this.mailProperties = mailProperties;
    }

    @Override
    public void sendEmailVerification(String toEmail, String recipientName, String verificationLink) {
        send(toEmail, content.verification(recipientName, verificationLink));
        audit("verification", toEmail);
    }

    @Override
    public void sendPasswordReset(String toEmail, String recipientName, String resetLink) {
        send(toEmail, content.passwordReset(recipientName, resetLink));
        audit("password-reset", toEmail);
    }

    @Override
    public void sendPasswordChangeVerification(String toEmail, String recipientName, String confirmLink) {
        send(toEmail, content.passwordChangeVerification(recipientName, confirmLink));
        audit("password-change-verification", toEmail);
    }

    @Override
    public void sendPasswordChangeOtp(String toEmail, String recipientName, String code, int expiryMinutes) {
        send(toEmail, content.passwordChangeOtp(recipientName, code, expiryMinutes));
        audit("password-change-otp", toEmail);
    }

    @Override
    public void sendPasswordChangedNotification(String toEmail, String recipientName) {
        send(toEmail, content.passwordChanged(recipientName));
        audit("password-changed", toEmail);
    }

    private void send(String toEmail, EmailMessage message) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());
            helper.setFrom(mailProperties.fromAddress(), mailProperties.fromName());
            helper.setTo(toEmail);
            helper.setSubject(message.subject());
            // (plainText, htmlText) -> multipart/alternative with a plain-text fallback.
            helper.setText(message.textBody(), message.htmlBody());
            mailSender.send(mime);
        } catch (MessagingException | UnsupportedEncodingException | MailException e) {
            // Audit the failure without leaking the token/link, then surface a stable code.
            log.error("Audit: failed to send e-mail to {}: {}", toEmail, e.getMessage());
            throw new ApiException(ErrorCode.EMAIL_SEND_FAILED, "Unable to send e-mail", e);
        }
    }

    /** Audit trail: records that a transactional e-mail was dispatched (never the token/link). */
    private void audit(String type, String toEmail) {
        log.info("Audit: sent {} e-mail to {}", type, toEmail);
    }
}
