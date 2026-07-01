package com.broksforge.modules.auth.email;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Builds branded Brok's Forge e-mails as transport-agnostic {@link EmailMessage}s:
 * a responsive, inline-styled HTML body plus a plain-text fallback for every
 * transactional message. Pure and deterministic (no I/O), so it is trivially
 * testable and shared by any {@link EmailService} implementation.
 *
 * <p>All caller-supplied values are HTML-escaped ({@link HtmlUtils#htmlEscape})
 * before interpolation, so a name or link can never inject markup into the HTML
 * part.</p>
 */
@Component
public class EmailContentFactory {

    private static final String BRAND = "Brok's Forge";
    private static final String PRIMARY = "#4f46e5";
    private static final String INK = "#0f172a";
    private static final String MUTED = "#64748b";
    private static final String BG = "#f1f5f9";

    public EmailMessage verification(String recipientName, String link) {
        String subject = "Verify your " + BRAND + " account";
        String html = layout("Confirm your e-mail",
                greeting(recipientName)
                        + paragraph("Welcome to " + esc(BRAND) + "! Confirm your e-mail address to activate "
                        + "your account.")
                        + button("Verify my e-mail", link)
                        + fallback(link)
                        + paragraph(muted("This link expires in 24 hours. If you did not create an account, "
                        + "you can safely ignore this e-mail.")));
        String text = greetingText(recipientName)
                + "Welcome to " + BRAND + "! Confirm your e-mail address to activate your account:\n\n"
                + link + "\n\nThis link expires in 24 hours. If you did not create an account, ignore this e-mail.\n\n"
                + "— " + BRAND;
        return new EmailMessage(subject, html, text);
    }

    public EmailMessage passwordReset(String recipientName, String link) {
        String subject = "Reset your " + BRAND + " password";
        String html = layout("Reset your password",
                greeting(recipientName)
                        + paragraph("We received a request to reset your " + esc(BRAND) + " password. "
                        + "Choose a new one using the button below.")
                        + button("Reset my password", link)
                        + fallback(link)
                        + paragraph(muted("This link expires in 1 hour. If you did not request a reset, ignore "
                        + "this e-mail — your password will not change.")));
        String text = greetingText(recipientName)
                + "We received a request to reset your " + BRAND + " password. Use this link:\n\n"
                + link + "\n\nThis link expires in 1 hour. If you did not request a reset, ignore this e-mail.\n\n"
                + "— " + BRAND;
        return new EmailMessage(subject, html, text);
    }

    public EmailMessage passwordChanged(String recipientName) {
        String subject = "Your " + BRAND + " password was changed";
        String html = layout("Password changed",
                greeting(recipientName)
                        + paragraph("This is a confirmation that your " + esc(BRAND) + " password was just changed.")
                        + paragraph(muted("If this wasn't you, reset your password immediately and contact "
                        + "support.")));
        String text = greetingText(recipientName)
                + "This is a confirmation that your " + BRAND + " password was just changed.\n"
                + "If this wasn't you, reset your password immediately and contact support.\n\n"
                + "— " + BRAND;
        return new EmailMessage(subject, html, text);
    }

    // ----------------------------------------------------------------------
    // HTML building blocks (inline styles — email clients strip <style> blocks)
    // ----------------------------------------------------------------------

    private String layout(String heading, String bodyHtml) {
        return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"></head>"
                + "<body style=\"margin:0;padding:0;background:" + BG + ";"
                + "font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;color:" + INK + ";\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:"
                + BG + ";padding:32px 0;\"><tr><td align=\"center\">"
                + "<table role=\"presentation\" width=\"480\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"width:480px;max-width:92%;background:#ffffff;border-radius:12px;overflow:hidden;"
                + "border:1px solid #e2e8f0;\">"
                + "<tr><td style=\"background:" + INK + ";padding:20px 28px;color:#ffffff;font-size:18px;"
                + "font-weight:600;\">⚒️ " + esc(BRAND) + "</td></tr>"
                + "<tr><td style=\"padding:28px;\">"
                + "<h1 style=\"margin:0 0 16px;font-size:20px;color:" + INK + ";\">" + esc(heading) + "</h1>"
                + bodyHtml
                + "</td></tr>"
                + "<tr><td style=\"padding:18px 28px;border-top:1px solid #e2e8f0;font-size:12px;color:" + MUTED
                + ";\">Sent by " + esc(BRAND) + " — The Engineering Platform for AI Agents. "
                + "This is an automated message; please do not reply.</td></tr>"
                + "</table></td></tr></table></body></html>";
    }

    private String greeting(String recipientName) {
        String name = recipientName == null || recipientName.isBlank() ? "there" : recipientName;
        return paragraph("Hi " + esc(name) + ",");
    }

    private String greetingText(String recipientName) {
        String name = recipientName == null || recipientName.isBlank() ? "there" : recipientName;
        return "Hi " + name + ",\n\n";
    }

    private String paragraph(String innerHtml) {
        return "<p style=\"margin:0 0 16px;font-size:15px;line-height:1.55;\">" + innerHtml + "</p>";
    }

    private String muted(String text) {
        return "<span style=\"color:" + MUTED + ";font-size:13px;\">" + esc(text) + "</span>";
    }

    private String button(String label, String url) {
        String href = esc(url);
        return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:8px 0 20px;\">"
                + "<tr><td style=\"border-radius:8px;background:" + PRIMARY + ";\">"
                + "<a href=\"" + href + "\" style=\"display:inline-block;padding:12px 22px;color:#ffffff;"
                + "text-decoration:none;font-weight:600;font-size:15px;border-radius:8px;\">" + esc(label) + "</a>"
                + "</td></tr></table>";
    }

    private String fallback(String url) {
        return "<p style=\"margin:0 0 16px;font-size:12px;color:" + MUTED + ";word-break:break-all;\">"
                + "Or paste this link into your browser:<br><a href=\"" + esc(url) + "\" style=\"color:" + PRIMARY
                + ";\">" + esc(url) + "</a></p>";
    }

    private String esc(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }
}
