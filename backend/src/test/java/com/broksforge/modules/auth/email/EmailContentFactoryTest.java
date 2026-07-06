package com.broksforge.modules.auth.email;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EmailContentFactory")
class EmailContentFactoryTest {

    private final EmailContentFactory factory = new EmailContentFactory();

    @Test
    @DisplayName("verification e-mail embeds the link in both HTML and text parts")
    void verificationEmail() {
        String link = "https://app.example.com/verify?token=abc123";
        EmailMessage message = factory.verification("Alice", link);

        assertThat(message.subject()).containsIgnoringCase("verify");
        assertThat(message.htmlBody()).contains(link).contains("Alice");
        assertThat(message.textBody()).contains(link);
    }

    @Test
    @DisplayName("OTP e-mail shows the code and its expiry")
    void otpEmail() {
        EmailMessage message = factory.passwordChangeOtp("Bob", "482913", 5);

        assertThat(message.htmlBody()).contains("482913");
        assertThat(message.textBody()).contains("482913").contains("5 minute");
    }

    @Test
    @DisplayName("HTML-escapes caller-supplied values to prevent markup injection")
    void escapesHtml() {
        EmailMessage message = factory.verification("<script>alert(1)</script>", "https://x/y");
        assertThat(message.htmlBody()).doesNotContain("<script>");
        assertThat(message.htmlBody()).contains("&lt;script&gt;");
    }
}
