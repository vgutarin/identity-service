package vg.identity.service;

import java.net.URI;
import java.util.Locale;

/** Builds and recognizes the canonical public URI used to identify a Telegram bot application. */
public final class TelegramUriBuilder {
    private static final String BOT_BASE_URI = "https://t.me/";

    private TelegramUriBuilder() {
    }

    public static URI botUri(String botUsername) {
        return URI.create(BOT_BASE_URI + botUsername.toLowerCase(Locale.ROOT));
    }

    public static boolean isBotUri(String uri) {
        return uri != null && uri.startsWith(BOT_BASE_URI);
    }
}
