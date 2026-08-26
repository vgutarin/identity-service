package vg.identity.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vg.identity.BaseIntegrationTest;
import vg.identity.entity.IdentityApplicationUserClaimEntity;
import vg.identity.model.IdentityApplicationUserPrincipal;
import vg.identity.model.TelegramAuthenticationRequest;
import vg.identity.rest.v1.IdentityApplicationApiRestClient;
import vg.unique.id.model.UniqueId;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityApplicationRestClientIntegrationTest extends BaseIntegrationTest {
    @Autowired
    private IdentityApplicationApiRestClient restClient;

    @Test
    void me_whenApiKeyHeaderIsMissing_returnsUnauthorized() throws Exception {
        createApplicationWithApiKey();

        var response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:8082/api/v1/applications/me"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void me_whenConfiguredApiKeyIsValid_returnsOnlyAuthenticatedApplicationMetadata() {
        var application = createApplicationWithApiKey();

        var response = restClient.me();

        assertThat(response.applicationUniqueId()).isEqualTo(new UniqueId(application.uniqueId()));
        assertThat(response.workspaceUniqueId()).isEqualTo(new UniqueId(application.workspaceUniqueId()));
        assertThat(response.name()).isEqualTo(application.name());
        assertThat(response.uri()).isEqualTo(URI.create(application.uri()));
        assertThat(response.getClass().getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("applicationUniqueId", "workspaceUniqueId", "name", "uri");
    }

    @Test
    void authenticateTelegram_whenConsentNotGiven_returnsProvisionalPrincipalAndStoresNothing() throws Exception {
        var botToken = "123456:integration-test-token";
        createApplicationWithApiKey("{\"token\":\"" + botToken + "\"}");
        var initData = telegramInitData(botToken, "{\"id\":42,\"first_name\":\"John\"}");

        var result = restClient.authenticateTelegram(TelegramAuthenticationRequest.builder().telegramInitData(initData).consentToKeepPersonalData(false).build());

        assertThat(result).isPresent();
        var principal = result.orElseThrow();
        assertThat(principal.userUniqueId()).isNull();
        assertThat(principal.name()).isEqualTo("John");
        assertThat(principal.permissions()).isEmpty();
        assertThat(principal.consentGiven()).isFalse();

        // Consent-before-storage: no user/channel was persisted for this Telegram identity.
        assertThat(channelRepository.findByChannelTypeAndChannelUserIdHash(
                vg.identity.model.IdentityChannelType.TELEGRAM_USER,
                encryptionService.hashCaseSensitive("42")
        )).isEmpty();
    }

    @Test
    void authenticateTelegram_whenConsentGiven_establishesUserWithStableSubjectAndScopedPermissions() throws Exception {
        var botToken = "123456:integration-test-token";
        var application = createApplicationWithApiKey("{\"token\":\"" + botToken + "\"}");
        var initData = telegramInitData(botToken, "{\"id\":42,\"first_name\":\"John\"}");

        var first = restClient.authenticateTelegram(TelegramAuthenticationRequest.builder().telegramInitData(initData).consentToKeepPersonalData(true).build());
        assertThat(first).isPresent();
        var principal = first.orElseThrow();
        assertThat(principal.userUniqueId()).isNotNull();
        assertThat(principal.name()).isEqualTo("John");
        assertThat(principal.consentGiven()).isTrue();
        assertThat(principal.permissions()).isEmpty();

        var user = channelRepository.findByChannelTypeAndChannelUserIdHash(
                        vg.identity.model.IdentityChannelType.TELEGRAM_USER,
                        encryptionService.hashCaseSensitive("42")
                )
                .map(channel -> channel.getIdentityUser())
                .orElseThrow();
        applicationUserClaimRepository.save(IdentityApplicationUserClaimEntity.builder()
                .application(applicationRepository.getReferenceById(application.uniqueId()))
                .identityUser(user)
                .scope(getScopeClaim(application.workspaceUniqueId(), IdentityApplicationUserPrincipal.PERMISSIONS_SCOPE))
                .claim(getScopeClaim(application.workspaceUniqueId(), "orders.read"))
                .build());

        var second = restClient.authenticateTelegram(TelegramAuthenticationRequest.builder().telegramInitData(initData).consentToKeepPersonalData(true).build());
        var third = restClient.authenticateTelegram(TelegramAuthenticationRequest.builder().telegramInitData(initData).consentToKeepPersonalData(false).build());

        assertThat(second.orElseThrow().userUniqueId()).isEqualTo(principal.userUniqueId());
        assertThat(second.orElseThrow().permissions()).containsExactly("orders.read");
        // Once established, an already-consented user still resolves to its identifier even when the request
        // omits consent (FR-007: a non-null userUniqueId always means consent was given).
        assertThat(third.orElseThrow().userUniqueId()).isEqualTo(principal.userUniqueId());
        assertThat(third.orElseThrow().consentGiven()).isTrue();
    }

    @Test
    void authenticateTelegram_whenInitDataIsInvalid_returnsNoPrincipalAndStoresNothing() {
        var botToken = "123456:integration-test-token";
        createApplicationWithApiKey("{\"token\":\"" + botToken + "\"}");

        // Invalid/unverifiable init data -> 204 (Optional.empty()) with nothing persisted, regardless of consent.
        for (var consent : new boolean[]{false, true}) {
            var result = restClient.authenticateTelegram(TelegramAuthenticationRequest.builder()
                    .telegramInitData("not-a-valid-init-data")
                    .consentToKeepPersonalData(consent)
                    .build());

            assertThat(result).isEmpty();
        }

        assertThat(userRepository.count()).isZero();
        assertThat(channelRepository.count()).isZero();
    }

}
