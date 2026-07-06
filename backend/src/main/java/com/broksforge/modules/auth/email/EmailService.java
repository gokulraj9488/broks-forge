package com.broksforge.modules.auth.email;

/**
 * Outbound transactional e-mail port.
 *
 * <p>The default implementation ({@code LoggingEmailService}) writes messages to
 * the application log, which is the correct behaviour for local development and
 * CI. A production deployment supplies an SMTP/provider-backed implementation
 * (e.g. {@code JavaMailSender}, SES, Postmark) as a drop-in replacement &mdash;
 * no calling code changes.</p>
 */
public interface EmailService {

    void sendEmailVerification(String toEmail, String recipientName, String verificationLink);

    void sendPasswordReset(String toEmail, String recipientName, String resetLink);

    void sendPasswordChangeVerification(String toEmail, String recipientName, String confirmLink);

    /**
     * Sends the 6-digit one-time code that authorises a password change
     * (see ADR 0017). {@code expiryMinutes} is stated in the message body.
     */
    void sendPasswordChangeOtp(String toEmail, String recipientName, String code, int expiryMinutes);

    void sendPasswordChangedNotification(String toEmail, String recipientName);
}
