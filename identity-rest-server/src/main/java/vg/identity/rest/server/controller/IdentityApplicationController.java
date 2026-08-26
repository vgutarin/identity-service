package vg.identity.rest.server.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vg.identity.model.IdentityApplicationPrincipal;
import vg.identity.model.IdentityApplicationUserPrincipal;
import vg.identity.model.TelegramAuthenticationRequest;
import vg.identity.service.IdentityApplicationApiService;

/**
 * API-key-authenticated application endpoints.
 *
 * <p>Registered only when {@code identity.rest.api.enabled=true}. The condition lives on the class
 * itself (not on an importing auto-configuration) because {@code identity-logic}'s
 * {@code @ComponentScan("vg.identity")} discovers this package; gating here guarantees the endpoints
 * exist in exactly one place and only when the REST API is enabled.</p>
 *
 * <p>The controller is a thin transport layer: it delegates to the embedded {@link IdentityApplicationApiService}
 * (the single source of the contract behavior) rather than re-building responses, so the remote and embedded
 * paths cannot drift (constitution Principle VI).</p>
 */
@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
@ConditionalOnBooleanProperty("identity.rest.api.enabled")
public class IdentityApplicationController {
    private final IdentityApplicationApiService applicationApi;

    @GetMapping("/me")
    public IdentityApplicationPrincipal me() {
        return applicationApi.me();
    }

    @PostMapping(value = "/me/authentications/telegram", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<IdentityApplicationUserPrincipal> authenticateTelegram(@RequestBody TelegramAuthenticationRequest request) {
        return applicationApi.authenticateTelegram(request)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
