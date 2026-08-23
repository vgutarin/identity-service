package vg.identity.rest.server.controller;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import vg.identity.model.IdentityApiKeyPrincipal;
import vg.identity.model.IdentityApplication;
import vg.identity.model.IdentityApplicationUserPrincipal;
import vg.identity.model.TelegramAuthenticationRequest;
import vg.identity.service.IdentityApplicationApiService;
import vg.identity.service.IdentityApplicationService;
import vg.unique.id.model.UniqueId;

import java.util.Optional;
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
        var request = TelegramAuthenticationRequest.builder().telegramInitData("init-data").consentToKeepPersonalData(true).build();
        var principal = IdentityApplicationUserPrincipal.builder()
                .sub("84")
                .name("Ada")
                .permissions(Set.of("orders.read"))
                .consentGiven(true)
                .build();
        when(applicationApi.authenticateTelegram(request)).thenReturn(Optional.of(principal));
        var controller = new IdentityApplicationController(applicationService, applicationApi);

        var response = controller.authenticateTelegram(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(principal);
    }

    @Test
    void authenticateTelegram_whenTelegramAuthenticationFails_returnsNoContent() {
        var applicationService = Mockito.mock(IdentityApplicationService.class);
        var applicationApi = Mockito.mock(IdentityApplicationApiService.class);
        var request = TelegramAuthenticationRequest.builder().telegramInitData("invalid").consentToKeepPersonalData(false).build();
        when(applicationApi.authenticateTelegram(request)).thenReturn(Optional.empty());
        var controller = new IdentityApplicationController(applicationService, applicationApi);

        var response = controller.authenticateTelegram(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void principal_usesOidcClaimNamesAndReservesLocaleAsAbsent() {
        var mapper = JsonMapper.builder().build();

        var established = IdentityApplicationUserPrincipal.builder().sub("84").name("Ada").permissions(Set.of("orders.read")).consentGiven(true).build();
        var json = mapper.readTree(mapper.writeValueAsString(established));
        // Identity fields use OIDC standard claim names; non-standard fields don't collide with them.
        assertThat(json.get("sub").asString()).isEqualTo("84");
        assertThat(json.get("name").asString()).isEqualTo("Ada");
        assertThat(json.get("consentGiven").asBoolean()).isTrue();
        assertThat(json.get("permissions")).isNotNull();
        // locale is a reserved OIDC claim that this feature never populates -> never emitted.
        assertThat(json.has("locale")).isFalse();

        // Pre-consent round-trips with a null subject (partial OIDC compatibility is permitted before consent).
        var preConsent = IdentityApplicationUserPrincipal.builder().name("Ada").consentGiven(false).build();
        var roundTripped = mapper.readValue(
                mapper.writeValueAsString(preConsent), IdentityApplicationUserPrincipal.class);
        assertThat(roundTripped.sub()).isNull();
        assertThat(roundTripped.consentGiven()).isFalse();
        assertThat(roundTripped.permissions()).isEmpty();
    }

    @Test
    void principal_toleratesUnknownFutureClaims_soAddingOptionalFieldsDoesNotBreakConsumers() {
        // Consumers read the principal through a mapper configured to ignore unknown properties (as Spring
        // Boot configures the rest client). A future version may add optional claims (e.g. locale) or other
        // fields; a consumer reading only the currently-defined fields must keep working (FR-016 / SC-007).
        var mapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        var futureJson = """
                {"sub":"84","name":"Ada","permissions":["orders.read"],"consentGiven":true,\
                "locale":"en-US","someFutureClaim":{"nested":123}}""";

        var principal = mapper.readValue(futureJson, IdentityApplicationUserPrincipal.class);

        assertThat(principal.sub()).isEqualTo("84");
        assertThat(principal.name()).isEqualTo("Ada");
        assertThat(principal.permissions()).containsExactly("orders.read");
        assertThat(principal.consentGiven()).isTrue();
    }
}
