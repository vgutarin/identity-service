package vg.identity.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import vg.identity.model.principal.IdentityInternalApplicationPrincipal;
import vg.unique.id.model.UniqueId;

/**
 * Resolves the application represented by the current API-key principal, or by the explicitly configured
 * trusted embedded application context when no security principal exists.
 */
@Service
public class CurrentIdentityApplicationService {
    private final CurrentPrincipalService currentPrincipalService;
    private final UniqueId embeddedApplicationUniqueId;

    public CurrentIdentityApplicationService(
            CurrentPrincipalService currentPrincipalService,
            @Value("${vg.identity.embedded.application-unique-id:}") String embeddedApplicationUniqueId
    ) {
        this.currentPrincipalService = currentPrincipalService;
        this.embeddedApplicationUniqueId = parseEmbeddedApplicationUniqueId(embeddedApplicationUniqueId);
    }

    /**
     * Returns the authenticated application principal, or throws when the current caller is not an
     * authenticated application. Unlike {@link #requireApplicationUniqueId()}, this never applies the
     * embedded-application fallback: a principal object only exists for a real API-key authentication,
     * so endpoints that describe "the authenticated application" (rather than merely act on its behalf)
     * must go through here.
     */
    public IdentityInternalApplicationPrincipal requireApplicationPrincipal() {
        var principal = currentPrincipalService.findCurrentPrincipal();
        if (principal instanceof IdentityInternalApplicationPrincipal applicationPrincipal) {
            return applicationPrincipal;
        }
        throw applicationAuthenticationRequired();
    }

    /**
     * Resolves the current application's unique id from the authenticated API-key principal, falling back
     * to the configured trusted embedded application when no security principal exists at all. A non-null
     * but non-application principal is always denied, even when an embedded application is configured.
     */
    public UniqueId requireApplicationUniqueId() {
        var principal = currentPrincipalService.findCurrentPrincipal();
        if (principal instanceof IdentityInternalApplicationPrincipal applicationPrincipal) {
            return applicationPrincipal.getUniqueId();
        }
        if (principal != null) {
            throw applicationAuthenticationRequired();
        }
        if (embeddedApplicationUniqueId != null) {
            return embeddedApplicationUniqueId;
        }
        throw applicationAuthenticationRequired();
    }

    private static AccessDeniedException applicationAuthenticationRequired() {
        return new AccessDeniedException("Application authentication is required");
    }

    private static UniqueId parseEmbeddedApplicationUniqueId(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            var uniqueId = Long.parseLong(value.trim());
            if (uniqueId <= 0) {
                throw invalidEmbeddedApplicationUniqueId();
            }
            return new UniqueId(uniqueId);
        } catch (NumberFormatException ignored) {
            throw invalidEmbeddedApplicationUniqueId();
        }
    }

    private static IllegalStateException invalidEmbeddedApplicationUniqueId() {
        return new IllegalStateException("Property 'vg.identity.embedded.application-unique-id' must be a positive application unique ID");
    }
}
