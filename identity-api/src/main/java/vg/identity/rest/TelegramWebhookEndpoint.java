package vg.identity.rest;

import vg.unique.id.model.UniqueId;

/** Public HTTP route for Telegram bot webhook delivery. */
public final class TelegramWebhookEndpoint {
    public static final String BASE_PATH = "/api/v1/telegram/webhooks";

    private TelegramWebhookEndpoint() {
    }

    public static String path(UniqueId applicationUniqueId) {
        return BASE_PATH + "/" + applicationUniqueId;
    }
}
