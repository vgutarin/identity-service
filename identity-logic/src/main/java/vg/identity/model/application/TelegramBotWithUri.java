package vg.identity.model.application;

import java.net.URI;

/**
 * A Telegram bot together with the public URL used to open it (e.g. {@code https://t.me/{botUsername}}).
 * <p>
 * The URL is used to build the external link that opens the bot, while the {@link TelegramBot} carries
 * the token required to validate the callback after the user interacts with it.
 * <p>
 * Modelled as a {@link URI} rather than {@link java.net.URL}: the value is only ever parsed, extended with
 * query parameters and rendered as text (never opened), and {@code URI} has safe, syntactic
 * {@code equals}/{@code hashCode} — unlike {@code URL}, whose equality performs blocking DNS resolution.
 */
public record TelegramBotWithUri(URI uri, TelegramBot bot) {
}
