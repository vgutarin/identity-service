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
 * Builds the "reset your password" message from the bilingual HTML template in
 * {@code resources/templates/email/reset-password.html.template} (Ukrainian first, English second).
 * <p>
 * As with {@link ConfirmEmailMailFactory}, the subject lives in a leading {@code <!-- subject: ... -->}
 * header and the single reset link is a {@code ${webUrl}} placeholder, so the wording/markup can be edited or
 * translated without touching code. There is no Telegram variant — a password reset is always a web link.
 */
@Service
public class ResetPasswordMailFactory {

    private static final String WEB_URL_PLACEHOLDER = "${webUrl}";
    private static final Pattern SUBJECT_HEADER = Pattern.compile("(?s)<!--\\s*subject:\\s*(.*?)\\s*-->\\s*");

    private final Template template = Template.load("templates/email/reset-password.html.template");

    /**
     * @param recipientEmail the address the reset email is sent to
     * @param resetUrl       the "set a new password" link (absolute when built by the frontend)
     */
    public EmailMessage create(String recipientEmail, URI resetUrl) {
        var body = template.body().replace(WEB_URL_PLACEHOLDER, escapeAttribute(resetUrl.toString()));
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
