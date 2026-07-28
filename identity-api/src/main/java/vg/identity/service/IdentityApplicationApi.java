package vg.identity.service;

import vg.identity.model.AuthenticatedIdentityApplication;
import vg.identity.model.IdentityApplicationUserPrincipal;

import java.util.Optional;

/**
 * Provides metadata about the application represented by the current application principal.
 */
public interface IdentityApplicationApi {
    AuthenticatedIdentityApplication me();

    /**
     * Verifies Telegram WebApp data and resolves the user in the current application's authorization scope.
     */
    Optional<IdentityApplicationUserPrincipal> authenticateTelegram(String initData);
}
