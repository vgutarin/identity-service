package vg.identity.service;

import vg.identity.model.AuthenticatedIdentityApplication;

/**
 * Provides metadata about the application represented by the current application principal.
 */
public interface IdentityApplicationApi {
    AuthenticatedIdentityApplication me();
}
