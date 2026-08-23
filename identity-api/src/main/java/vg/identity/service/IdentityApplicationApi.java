package vg.identity.service;

import vg.identity.model.AuthenticatedIdentityApplication;
import vg.identity.model.IdentityApplicationUserPrincipal;
import vg.identity.model.TelegramAuthenticationRequest;

import java.util.Optional;

/**
 * Provides metadata about the application represented by the current application principal.
 */
public interface IdentityApplicationApi {
    AuthenticatedIdentityApplication me();

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
}
