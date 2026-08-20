package vg.identity.service;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class ResetPasswordMailFactoryTest {

    private final ResetPasswordMailFactory factory = new ResetPasswordMailFactory();

    @Test
    void create_buildsBilingualHtmlEmailWithSubstitutedResetLink() {
        var email = factory.create("john@example.com", URI.create("https://id.example.com/reset/password/42_secret"));

        assertThat(email.to()).containsExactly("john@example.com");
        assertThat(email.html()).isTrue();
        // Bilingual subject: Ukrainian first, English second (from the <!-- subject: ... --> header).
        assertThat(email.subject())
                .contains("Скидання пароля")
                .contains("Reset your password");
        // Bilingual body with the reset link substituted for ${webUrl}, and no leftover placeholder.
        assertThat(email.body())
                .contains("Встановити новий пароль")
                .contains("Set a new password")
                .contains("https://id.example.com/reset/password/42_secret")
                .doesNotContain("${webUrl}");
    }

    @Test
    void create_escapesHtmlSensitiveCharactersInTheLink() {
        var email = factory.create("john@example.com", URI.create("https://id.example.com/reset?a=1&b=2"));

        // The ampersand from the URL must be HTML-attribute-escaped in the href.
        assertThat(email.body())
                .contains("https://id.example.com/reset?a=1&amp;b=2")
                .doesNotContain("a=1&b=2");
    }
}
