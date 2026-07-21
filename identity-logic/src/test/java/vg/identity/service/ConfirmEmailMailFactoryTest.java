package vg.identity.service;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class ConfirmEmailMailFactoryTest {

    private static final String WEB_URL = "https://id.example/verify/123";
    private static final String TELEGRAM_URL = "https://t.me/identityvgbot?startapp=123";

    private final ConfirmEmailMailFactory factory = new ConfirmEmailMailFactory();

    @Test
    void create_withTelegramLink_buildsBilingualHtmlWithPreferredTelegramAndWebLinks() {
        var email = factory.create("john@example.com", URI.create(WEB_URL), URI.create(TELEGRAM_URL));

        assertThat(email.to()).containsExactly("john@example.com");
        assertThat(email.html()).isTrue();
        assertThat(email.subject())
                .isEqualTo("Підтвердьте вашу електронну адресу / Confirm your email address");

        var body = email.body();
        assertThat(body)
                .contains("Вітаємо!")
                .contains("Hello,")
                .contains("Підтвердити через Telegram (рекомендовано)")
                .contains("Confirm via Telegram (recommended)")
                .contains(TELEGRAM_URL)
                .contains("Підтвердити через вебсайт")
                .contains("Confirm using web site")
                .contains(WEB_URL);

        // Ukrainian block comes first, English second.
        assertThat(body.indexOf("Вітаємо!")).isLessThan(body.indexOf("Hello,"));
        // Telegram is the preferred (primary) action and appears before the web fallback in each block.
        assertThat(body.indexOf(TELEGRAM_URL)).isLessThan(body.indexOf(WEB_URL));
    }

    @Test
    void create_withoutTelegramLink_buildsWebOnlyBilingualHtml() {
        var email = factory.create("john@example.com", URI.create(WEB_URL), null);

        assertThat(email.html()).isTrue();
        assertThat(email.body())
                .contains("Вітаємо!")
                .contains("Hello,")
                .contains("Підтвердити через вебсайт")
                .contains("Confirm using web site")
                .contains(WEB_URL)
                .doesNotContain("Telegram")
                .doesNotContain("t.me");
    }

    @Test
    void create_escapesAmpersandsInUrlsForHtmlAttributeContext() {
        var email = factory.create("john@example.com", URI.create("https://id.example/verify?id=1&x=2"), null);

        assertThat(email.body()).contains("https://id.example/verify?id=1&amp;x=2");
    }
}
