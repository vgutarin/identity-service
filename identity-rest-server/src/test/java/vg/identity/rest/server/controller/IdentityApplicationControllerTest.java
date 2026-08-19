package vg.identity.rest.server.controller;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import vg.identity.model.IdentityApiKeyPrincipal;
import vg.identity.model.IdentityApplication;
import vg.identity.model.IdentityApplicationUserPrincipal;
import vg.identity.service.IdentityApplicationApiService;
import vg.identity.service.IdentityApplicationService;
import vg.unique.id.model.UniqueId;

import java.util.Optional;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class IdentityApplicationControllerTest {

    @Test
    void me_whenApplicationIsAuthenticated_returnsOnlySafeApplicationFields() {
        var applicationService = Mockito.mock(IdentityApplicationService.class);
        var applicationApi = Mockito.mock(IdentityApplicationApiService.class);
        var principal = new IdentityApiKeyPrincipal(new UniqueId(42L), "https://example.test/app");
        when(applicationService.getAuthenticatedApplication(principal.getUniqueId())).thenReturn(
                IdentityApplication.builder()
                        .uniqueId(principal.getUniqueId())
                        .workspaceUniqueId(7L)
                        .name("Orders")
                        .uri("https://example.test/app")
                        .payload("must-not-be-serialized")
                        .build()
        );
        var controller = new IdentityApplicationController(applicationService, applicationApi);

        var response = controller.me(principal);

        assertThat(response.uniqueId()).isEqualTo(principal.getUniqueId().toString());
        assertThat(response.workspaceUniqueId()).isEqualTo(7L);
        assertThat(response.name()).isEqualTo("Orders");
        assertThat(response.uri()).isEqualTo("https://example.test/app");
        assertThat(response.getClass().getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("payload");
    }

    @Test
    void authenticateTelegram_whenTelegramAuthenticationSucceeds_returnsApplicationUserPrincipal() {
        var applicationService = Mockito.mock(IdentityApplicationService.class);
        var applicationApi = Mockito.mock(IdentityApplicationApiService.class);
        var principal = new IdentityApplicationUserPrincipal(
                "42",
                "84",
                Map.of(IdentityApplicationUserPrincipal.PERMISSIONS_SCOPE, Set.of("orders.read"))
        );
        when(applicationApi.authenticateTelegram("init-data")).thenReturn(Optional.of(principal));
        var controller = new IdentityApplicationController(applicationService, applicationApi);

        var response = controller.authenticateTelegram("init-data");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(principal);
    }

    @Test
    void authenticateTelegram_whenTelegramAuthenticationFails_returnsNoContent() {
        var applicationService = Mockito.mock(IdentityApplicationService.class);
        var applicationApi = Mockito.mock(IdentityApplicationApiService.class);
        when(applicationApi.authenticateTelegram(null)).thenReturn(Optional.empty());
        var controller = new IdentityApplicationController(applicationService, applicationApi);

        var response = controller.authenticateTelegram(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
    }
}
