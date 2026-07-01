package com.broksforge.modules.auth.email;

/**
 * A rendered, transport-agnostic e-mail: a subject plus both an HTML body and a
 * plain-text fallback. Built by {@link EmailContentFactory} and consumed by an
 * {@link EmailService} implementation (e.g. {@code SmtpEmailService} sends both
 * parts as a {@code multipart/alternative} message).
 */
public record EmailMessage(String subject, String htmlBody, String textBody) {
}
