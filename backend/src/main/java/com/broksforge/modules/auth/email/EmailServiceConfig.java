package com.broksforge.modules.auth.email;

import com.broksforge.config.properties.MailProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Chooses the single active {@link EmailService} implementation based on whether SMTP is
 * actually configured, rather than on the active Spring profile. {@code spring.mail.host}
 * is the signal: set it (any profile) and {@link SmtpEmailService} activates; leave it
 * unset (including in {@code prod}) and {@link LoggingEmailService} is used instead — so a
 * production deployment without SMTP configured still boots and every e-mail-triggering
 * flow still works end-to-end, just via console-logged links instead of real e-mail.
 *
 * <p>Declaration order matters here: {@code loggingEmailService}'s
 * {@link ConditionalOnMissingBean} is evaluated after {@code smtpEmailService} within this
 * single {@code @Configuration} class, which Spring guarantees processes {@code @Bean}
 * methods in declaration order — the same pattern Spring Boot's own autoconfiguration
 * classes use for "configured implementation, else default" wiring.</p>
 */
@Configuration
public class EmailServiceConfig {

    @Bean
    @ConditionalOnProperty(prefix = "spring.mail", name = "host")
    public EmailService smtpEmailService(JavaMailSender mailSender,
                                          EmailContentFactory content,
                                          MailProperties mailProperties) {
        return new SmtpEmailService(mailSender, content, mailProperties);
    }

    @Bean
    @ConditionalOnMissingBean(EmailService.class)
    public EmailService loggingEmailService() {
        return new LoggingEmailService();
    }
}
