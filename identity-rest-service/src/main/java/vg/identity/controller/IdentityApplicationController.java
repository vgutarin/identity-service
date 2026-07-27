package vg.identity.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vg.identity.model.IdentityApiKeyPrincipal;
import vg.identity.service.IdentityApplicationService;

/**
 * API-key-authenticated application endpoints.
 */
@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
public class IdentityApplicationController {
    private final IdentityApplicationService applicationService;

    @GetMapping("/me")
    public AuthenticatedApplicationResponse me(@AuthenticationPrincipal IdentityApiKeyPrincipal principal) {
        var application = applicationService.getApiKeyAuthenticatedApplication(principal.getUniqueId());
        return new AuthenticatedApplicationResponse(
                application.getUniqueId().toString(),
                application.getWorkspaceUniqueId(),
                application.getName(),
                application.getUri()
        );
    }

    public record AuthenticatedApplicationResponse(
            String uniqueId,
            Long workspaceUniqueId,
            String name,
            String uri
    ) {
    }
}
