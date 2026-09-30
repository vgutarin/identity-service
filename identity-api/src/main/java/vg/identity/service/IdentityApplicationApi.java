package vg.identity.service;

import vg.identity.model.IdentityApplicationPrincipal;
import vg.identity.model.IdentityApplicationUserPrincipal;
import vg.identity.model.IdentityApplicationTelegramPhone;
import vg.identity.model.TelegramAuthenticationRequest;
import vg.unique.id.model.UniqueId;

import java.util.Optional;

/**
 * Provides metadata about the application represented by the current application principal.
 */
public interface IdentityApplicationApi {
    IdentityApplicationPrincipal me();

    /**
     * Verifies Telegram WebApp data and resolves a consent-gated principal in the current application's
     * authorization scope.
     *
     * <p>When {@code consentToKeepPersonalData} is {@code false}, no user is created or persisted and the
     * returned principal is provisional ({@code sub == null}, empty permissions, {@code consentGiven == false}).
     * When {@code true}, the user is created if needed and an established principal (non-null {@code sub}) is
     * returned. An empty result means the Telegram data was invalid, unverifiable, or not resolvable — and,
     * in that case, nothing is persisted regardless of the consent flag.</p>
     *
     * @param request  the Telegram init data plus the user's consent-to-keep-personal-data flag
     */
    Optional<IdentityApplicationUserPrincipal> authenticateTelegram(TelegramAuthenticationRequest request);

    /**
     * Returns the phone number that this application is explicitly allowed to read after the user shared it with
     * the application's Telegram bot. An empty result deliberately conflates absent, unavailable, and
     * unavailable numbers.
     */
    Optional<IdentityApplicationTelegramPhone> findTelegramPhone(UniqueId userUniqueId);

    /** Revokes only the current application's access to the user's Telegram-shared phone number. */
    void revokeTelegramPhone(UniqueId userUniqueId);
}
