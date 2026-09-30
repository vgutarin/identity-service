package vg.identity.rest.server.controller;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import vg.identity.model.IdentityApplicationPrincipal;
import vg.identity.model.IdentityApplicationUserPrincipal;
import vg.identity.model.IdentityApplicationTelegramPhone;
import vg.identity.model.TelegramAuthenticationRequest;
import vg.identity.service.IdentityApplicationApiService;
import vg.unique.id.model.UniqueId;

import java.net.URI;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class IdentityApplicationControllerTest {

    @Test
    void me_delegatesToEmbeddedApi() {
        var applicationApi = Mockito.mock(IdentityApplicationApiService.class);
        var expected = new IdentityApplicationPrincipal(
                new UniqueId(42L), new UniqueId(7L), "Orders", URI.create("https://example.test/app"));
        when(applicationApi.me()).thenReturn(expected);
        var controller = new IdentityApplicationController(applicationApi);

        var response = controller.me();

        // The controller is a thin transport layer: it returns exactly what the embedded API produces
        // (Principle VI — no divergent re-mapping). The safe-fields contract is asserted in the service test.
        assertThat(response).isSameAs(expected);
    }

    @Test
    void authenticateTelegram_whenTelegramAuthenticationSucceeds_returnsApplicationUserPrincipal() {
        var applicationApi = Mockito.mock(IdentityApplicationApiService.class);
        var request = TelegramAuthenticationRequest.builder().telegramInitData("init-data").consentToKeepPersonalData(true).build();
        var principal = IdentityApplicationUserPrincipal.builder()
                .userUniqueId(new UniqueId(84L))
                .name("Ada")
                .permissions(Set.of("orders.read"))
                .consentGiven(true)
                .build();
        when(applicationApi.authenticateTelegram(request)).thenReturn(Optional.of(principal));
        var controller = new IdentityApplicationController(applicationApi);

        var response = controller.authenticateTelegram(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(principal);
    }

    @Test
    void authenticateTelegram_whenTelegramAuthenticationFails_returnsNoContent() {
        var applicationApi = Mockito.mock(IdentityApplicationApiService.class);
        var request = TelegramAuthenticationRequest.builder().telegramInitData("invalid").consentToKeepPersonalData(false).build();
        when(applicationApi.authenticateTelegram(request)).thenReturn(Optional.empty());
        var controller = new IdentityApplicationController(applicationApi);

        var response = controller.authenticateTelegram(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void findTelegramPhone_whenGrantExists_returnsPhone() {
        var applicationApi = Mockito.mock(IdentityApplicationApiService.class);
        var userUniqueId = new UniqueId(84L);
        var phone = new IdentityApplicationTelegramPhone("+14155552671");
        when(applicationApi.findTelegramPhone(userUniqueId)).thenReturn(Optional.of(phone));
        var controller = new IdentityApplicationController(applicationApi);

        var response = controller.findTelegramPhone(userUniqueId.toString());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(phone);
    }

    @Test
    void findTelegramPhone_whenUnavailable_returnsNoContent() {
        var applicationApi = Mockito.mock(IdentityApplicationApiService.class);
        var userUniqueId = new UniqueId(84L);
        when(applicationApi.findTelegramPhone(userUniqueId)).thenReturn(Optional.empty());
        var controller = new IdentityApplicationController(applicationApi);

        var response = controller.findTelegramPhone(userUniqueId.toString());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void revokeTelegramPhone_delegatesToEmbeddedApi() {
        var applicationApi = Mockito.mock(IdentityApplicationApiService.class);
        var userUniqueId = new UniqueId(84L);
        var controller = new IdentityApplicationController(applicationApi);

        var response = controller.revokeTelegramPhone(userUniqueId.toString());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        Mockito.verify(applicationApi).revokeTelegramPhone(userUniqueId);
    }

    @Test
    void principal_usesDomainNativeNamesAndReservesPreferredLocaleAsAbsent() {
        var mapper = JsonMapper.builder().build();

        var established = IdentityApplicationUserPrincipal.builder().userUniqueId(new UniqueId(84L)).name("Ada").permissions(Set.of("orders.read")).consentGiven(true).build();
        var json = mapper.readTree(mapper.writeValueAsString(established));
        // Identity fields use domain-native, self-explaining names — not OIDC standard claim names.
        assertThat(json.get("userUniqueId").asString()).isEqualTo(new UniqueId(84L).toString());
        assertThat(json.has("sub")).isFalse();
        assertThat(json.get("name").asString()).isEqualTo("Ada");
        assertThat(json.get("consentGiven").asBoolean()).isTrue();
        assertThat(json.get("permissions")).isNotNull();
        // The reserved preferred-locale placeholder is never populated -> never emitted; no OIDC `locale`.
        assertThat(json.has("preferredLocale")).isFalse();
        assertThat(json.has("locale")).isFalse();

        // Pre-consent round-trips with a null userUniqueId.
        var preConsent = IdentityApplicationUserPrincipal.builder().name("Ada").consentGiven(false).build();
        var roundTripped = mapper.readValue(
                mapper.writeValueAsString(preConsent), IdentityApplicationUserPrincipal.class);
        assertThat(roundTripped.userUniqueId()).isNull();
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
                {"userUniqueId":"%s","name":"Ada","permissions":["orders.read"],"consentGiven":true,\
                "preferredLocale":"en-US","someFutureClaim":{"nested":123}}""".formatted(new UniqueId(84L));

        var principal = mapper.readValue(futureJson, IdentityApplicationUserPrincipal.class);

        assertThat(principal.userUniqueId()).isEqualTo(new UniqueId(84L));
        assertThat(principal.name()).isEqualTo("Ada");
        assertThat(principal.permissions()).containsExactly("orders.read");
        assertThat(principal.consentGiven()).isTrue();
    }
}
