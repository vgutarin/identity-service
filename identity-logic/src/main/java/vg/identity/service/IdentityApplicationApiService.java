package vg.identity.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import vg.identity.model.AuthenticatedIdentityApplication;
import vg.identity.model.IdentityApplicationPrincipal;

/**
 * Embedded implementation of {@link IdentityApplicationApi}.
 */
@Service
@RequiredArgsConstructor
public class IdentityApplicationApiService implements IdentityApplicationApi {
    private final CurrentUserService currentUserService;
    private final IdentityApplicationService applicationService;

    @Override
    public AuthenticatedIdentityApplication me() {
        var principal = currentApplicationPrincipal();
        var application = applicationService.getAuthenticatedApplication(principal.getUniqueId());
        return new AuthenticatedIdentityApplication(
                application.getUniqueId().toString(),
                application.getWorkspaceUniqueId(),
                application.getName(),
                application.getUri()
        );
    }

    private IdentityApplicationPrincipal currentApplicationPrincipal() {
        var principal = currentUserService.findCurrentUserDetails();
        if (!(principal instanceof IdentityApplicationPrincipal applicationPrincipal)) {
            throw new AccessDeniedException("Application authentication is required");
        }
        return applicationPrincipal;
    }
}
