package vg.identity.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vg.identity.model.AuthenticatedIdentityApplication;
import vg.identity.model.IdentityApplicationPrincipal;
import vg.identity.model.IdentityApplicationUserPrincipal;
import vg.identity.service.IdentityApplicationApiService;
import vg.identity.service.IdentityApplicationService;

/**
 * API-key-authenticated application endpoints.
 */
@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
public class IdentityApplicationController {
    private final IdentityApplicationService applicationService;
    private final IdentityApplicationApiService applicationApi;

    @GetMapping("/me")
    public AuthenticatedIdentityApplication me(@AuthenticationPrincipal IdentityApplicationPrincipal principal) {
        var application = applicationService.getAuthenticatedApplication(principal.getUniqueId());
        return new AuthenticatedIdentityApplication(
                application.getUniqueId().toString(),
                application.getWorkspaceUniqueId(),
                application.getName(),
                application.getUri()
        );
    }

    @PostMapping(value = "/me/authentications/telegram", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<IdentityApplicationUserPrincipal> authenticateTelegram(@RequestBody(required = false) String initData) {
        return applicationApi.authenticateTelegram(initData)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
