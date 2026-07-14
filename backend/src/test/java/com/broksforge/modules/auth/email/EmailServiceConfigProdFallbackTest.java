package com.broksforge.modules.auth.email;

import com.broksforge.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the Railway incident where activating the {@code prod} profile
 * without any SMTP configuration crashed the whole application at startup with
 * "Error processing condition on ...MailSenderAutoConfiguration" (an unresolvable
 * {@code ${SPRING_MAIL_HOST}} placeholder with no default, inspected by
 * MailSenderAutoConfiguration's condition before any bean is even created).
 *
 * <p>Asserts the {@code prod} profile now boots cleanly with no SMTP configured, and that
 * {@link EmailServiceConfig} falls back to {@link LoggingEmailService} rather than trying
 * (and failing) to wire {@link SmtpEmailService}.</p>
 */
@ActiveProfiles({"prod", "test"})
class EmailServiceConfigProdFallbackTest extends AbstractIntegrationTest {

    @Autowired
    private EmailService emailService;

    @Test
    void prodProfileWithoutSmtpConfigFallsBackToLoggingEmailService() {
        assertThat(emailService).isInstanceOf(LoggingEmailService.class);
    }
}
