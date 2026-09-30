package vg.identity.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Minimal, untrusted Telegram webhook shape required to handle a self-shared phone contact.
 */
public record TelegramWebhookUpdate(
        TelegramWebhookMessage message
) {
    public record TelegramWebhookMessage(TelegramWebhookUser from, TelegramWebhookChat chat,
                                         TelegramWebhookContact contact) {
    }

    public record TelegramWebhookUser(Long id) {
    }

    public record TelegramWebhookChat(String type) {
    }

    public record TelegramWebhookContact(
            @JsonProperty("phone_number") String phoneNumber,
            @JsonProperty("user_id") Long userId
    ) {
    }
}
