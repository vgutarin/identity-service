package vg.identity.model.application;

import java.net.URL;

/**
 * A Telegram bot together with the public URL used to open it (e.g. {@code https://t.me/{botUsername}}).
 * <p>
 * The URL is used to build the external link that opens the bot, while the {@link TelegramBot} carries
 * the token required to validate the callback after the user interacts with it.
 */
public record TelegramBotWithUrl(URL url, TelegramBot bot) {
}
