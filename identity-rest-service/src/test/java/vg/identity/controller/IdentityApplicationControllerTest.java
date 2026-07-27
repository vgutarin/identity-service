package vg.identity.controller;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import vg.identity.model.IdentityApiKeyPrincipal;
import vg.identity.model.IdentityApplication;
import vg.identity.service.IdentityApplicationService;
import vg.unique.id.model.UniqueId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class IdentityApplicationControllerTest {

    @Test
    void me_whenApplicationIsAuthenticated_returnsOnlySafeApplicationFields() {
        var applicationService = Mockito.mock(IdentityApplicationService.class);
        var principal = new IdentityApiKeyPrincipal(new UniqueId(42L), "https://example.test/app");
        when(applicationService.getApiKeyAuthenticatedApplication(principal.getUniqueId())).thenReturn(
                IdentityApplication.builder()
                        .uniqueId(principal.getUniqueId())
                        .workspaceUniqueId(7L)
                        .name("Orders")
                        .uri("https://example.test/app")
                        .payload("must-not-be-serialized")
                        .build()
        );
        var controller = new IdentityApplicationController(applicationService);

        var response = controller.me(principal);

        assertThat(response.uniqueId()).isEqualTo(principal.getUniqueId().toString());
        assertThat(response.workspaceUniqueId()).isEqualTo(7L);
        assertThat(response.name()).isEqualTo("Orders");
        assertThat(response.uri()).isEqualTo("https://example.test/app");
        assertThat(response.getClass().getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("payload");
    }
}
