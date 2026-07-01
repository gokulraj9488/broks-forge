package com.broksforge.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Branding for outbound transactional e-mail (the {@code From} identity). The SMTP
 * connection itself is configured via Spring's own {@code spring.mail.*} properties
 * (host/port/credentials), sourced entirely from the environment — never hardcoded.
 *
 * @param fromAddress the envelope/from e-mail address (e.g. no-reply@yourdomain.com)
 * @param fromName    the display name shown to recipients (e.g. "Brok's Forge")
 */
@Validated
@ConfigurationProperties(prefix = "broksforge.mail")
public record MailProperties(String fromAddress, String fromName) {
}
