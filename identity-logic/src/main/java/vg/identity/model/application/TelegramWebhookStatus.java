package vg.identity.model.application;

import lombok.Builder;

/** Safe administration result: deliberately excludes the returned webhook URL and bot credentials. */
@Builder(toBuilder = true)
public record TelegramWebhookStatus(TelegramWebhookState state, boolean toggleAllowed, int applicationVersion) {
}
