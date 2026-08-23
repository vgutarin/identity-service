package vg.identity.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vg.identity.BaseIntegrationTest;
import vg.identity.entity.IdentityApplicationUserClaimEntity;
import vg.identity.model.IdentityApplicationUserPrincipal;
import vg.identity.model.TelegramAuthenticationRequest;
import vg.identity.rest.v1.IdentityApplicationApiRestClient;
import vg.unique.id.model.UniqueId;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.stream.Collectors;

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

        assertThat(response.uniqueId()).isEqualTo(new UniqueId(application.uniqueId()).toString());
        assertThat(response.workspaceUniqueId()).isEqualTo(application.workspaceUniqueId());
        assertThat(response.name()).isEqualTo(application.name());
        assertThat(response.uri()).isEqualTo(application.uri());
        assertThat(response.getClass().getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("uniqueId", "workspaceUniqueId", "name", "uri");
    }

    @Test
    void authenticateTelegram_whenConsentNotGiven_returnsProvisionalPrincipalAndStoresNothing() throws Exception {
        var botToken = "123456:integration-test-token";
        createApplicationWithApiKey("{\"token\":\"" + botToken + "\"}");
        var initData = initData(botToken, "{\"id\":42,\"first_name\":\"John\"}");

        var result = restClient.authenticateTelegram(TelegramAuthenticationRequest.builder().telegramInitData(initData).consentToKeepPersonalData(false).build());

        assertThat(result).isPresent();
        var principal = result.orElseThrow();
        assertThat(principal.sub()).isNull();
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
        var initData = initData(botToken, "{\"id\":42,\"first_name\":\"John\"}");

        var first = restClient.authenticateTelegram(TelegramAuthenticationRequest.builder().telegramInitData(initData).consentToKeepPersonalData(true).build());
        assertThat(first).isPresent();
        var principal = first.orElseThrow();
        assertThat(principal.sub()).isNotBlank();
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

        assertThat(second.orElseThrow().sub()).isEqualTo(principal.sub());
        assertThat(second.orElseThrow().permissions()).containsExactly("orders.read");
        // Once established, an already-consented user still resolves to its subject even when the request
        // omits consent (FR-007: a non-null sub always means consent was given).
        assertThat(third.orElseThrow().sub()).isEqualTo(principal.sub());
        assertThat(third.orElseThrow().consentGiven()).isTrue();
    }

    private static String initData(String botToken, String userJson) throws Exception {
        var parameters = Map.of(
                "auth_date", String.valueOf(Instant.now().getEpochSecond()),
                "query_id", "integration-test",
                "user", userJson
        );
        var dataCheckString = parameters.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("\n"));
        var hash = HexFormat.of().formatHex(hmacSha256(
                hmacSha256(bytes("WebAppData"), bytes(botToken)),
                bytes(dataCheckString)
        ));
        return parameters.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&")) + "&hash=" + hash;
    }

    private static byte[] hmacSha256(byte[] key, byte[] value) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(value);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

}
