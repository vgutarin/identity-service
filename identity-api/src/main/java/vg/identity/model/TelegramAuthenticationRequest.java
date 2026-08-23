package vg.identity.model;

import lombok.Builder;

/**
 * Request payload for the Telegram authentication operation.
 *
 * <p>{@code telegramInitData} is the raw Telegram WebApp init data, untrusted until verified server-side
 * and carried in the request body (never the URL). {@code consentToKeepPersonalData} indicates whether
 * the user granted consent to keep their personal data on this request; when absent it deserializes to
 * {@code false} (the safe default), so a user is created and persisted only on explicit consent.</p>
 */
@Builder
public record TelegramAuthenticationRequest(
        String telegramInitData,
        boolean consentToKeepPersonalData
) {
}
