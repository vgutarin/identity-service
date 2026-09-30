package vg.identity.service;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramUriBuilderTest {

    @Test
    void botUri_normalizesTheTelegramBotUri() {
        assertThat(TelegramUriBuilder.botUri("Orders_Bot"))
                .isEqualTo(URI.create("https://t.me/orders_bot"));
    }

    @Test
    void isBotUri_identifiesOnlyTheCanonicalTelegramBotUriPrefix() {
        assertThat(TelegramUriBuilder.isBotUri("https://t.me/orders_bot")).isTrue();
        assertThat(TelegramUriBuilder.isBotUri("https://example.test/t.me/orders_bot")).isFalse();
        assertThat(TelegramUriBuilder.isBotUri(null)).isFalse();
    }
}
