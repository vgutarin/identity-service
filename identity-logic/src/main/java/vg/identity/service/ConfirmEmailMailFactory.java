package vg.identity.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import vg.identity.model.EmailMessage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Builds the "confirm your email" message from HTML templates in {@code resources/templates/email}.
 * <p>
 * Each template is a complete, nicely formatted bilingual email (Ukrainian first, English second) whose subject
 * lives in a leading {@code <!-- subject: ... -->} header and whose confirmation links are {@code ${...}}
 * placeholders. Two variants are used depending on whether a Telegram bot is configured:
 * <ul>
 *     <li>{@code confirm-email.html.template} — preferred "Confirm via Telegram" button plus a secondary
 *     "Confirm using web site" link ({@code ${telegramUrl}} and {@code ${webUrl}});</li>
 *     <li>{@code confirm-email-web-site-only.html.template} — a single "Confirm using web site" button
 *     ({@code ${webUrl}}).</li>
 * </ul>
 * Keeping the markup and wording in resources means it can be edited/translated without touching code.
 */
@Service
public class ConfirmEmailMailFactory {

    private static final String WEB_URL_PLACEHOLDER = "${webUrl}";
    private static final String TELEGRAM_URL_PLACEHOLDER = "${telegramUrl}";
    private static final Pattern SUBJECT_HEADER = Pattern.compile("(?s)<!--\\s*subject:\\s*(.*?)\\s*-->\\s*");

    private final Template withTelegram = Template.load("templates/email/confirm-email.html.template");
    private final Template webOnly = Template.load("templates/email/confirm-email-web-site-only.html.template");

    /**
     * @param recipientEmail     the address the confirmation email is sent to
     * @param webConfirmUrl      the "confirm using web site" link (always present); may be relative
     * @param telegramConfirmUrl the preferred Telegram confirmation link, or {@code null} when no bot is configured
     */
    public EmailMessage create(String recipientEmail, URI webConfirmUrl, URI telegramConfirmUrl) {
        var template = telegramConfirmUrl != null ? withTelegram : webOnly;

        var body = template.body().replace(WEB_URL_PLACEHOLDER, escapeAttribute(webConfirmUrl.toString()));
        if (telegramConfirmUrl != null) {
            body = body.replace(TELEGRAM_URL_PLACEHOLDER, escapeAttribute(telegramConfirmUrl.toString()));
        }

        return EmailMessage.builder()
                .to(List.of(recipientEmail))
                .subject(template.subject())
                .body(body)
                .html(true)
                .build();
    }

    private static String escapeAttribute(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private record Template(String subject, String body) {

        static Template load(String path) {
            var content = read(path);
            var matcher = SUBJECT_HEADER.matcher(content);
            if (!matcher.find()) {
                throw new IllegalStateException(
                        "Email template " + path + " is missing a <!-- subject: ... --> header"
                );
            }
            var body = content.substring(0, matcher.start()) + content.substring(matcher.end());
            return new Template(matcher.group(1).trim(), body.strip());
        }

        private static String read(String path) {
            try {
                return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot load email template " + path, e);
            }
        }
    }
}
