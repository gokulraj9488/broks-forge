package com.broksforge.modules.auth.email;

import com.broksforge.common.exception.ApiException;
import com.broksforge.common.exception.ErrorCode;
import com.broksforge.config.properties.MailProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * Production e-mail transport. Sends real branded messages over SMTP using Spring's
 * {@link JavaMailSender}, as a {@code multipart/alternative} message carrying both an
 * HTML body and a plain-text fallback (built by {@link EmailContentFactory}).
 *
 * <p>Active only under the {@code prod} profile; local development and CI use the
 * console {@code LoggingEmailService} instead. All SMTP connection details and
 * credentials come from {@code spring.mail.*} environment variables — nothing is
 * hardcoded. {@link com.broksforge.modules.auth.service.AuthService} depends only on
 * the {@link EmailService} abstraction and is unaware which transport is active.</p>
 */
@Slf4j
@Service
@Profile("prod")
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
