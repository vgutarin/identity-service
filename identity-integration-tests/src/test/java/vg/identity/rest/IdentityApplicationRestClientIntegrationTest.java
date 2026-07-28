package vg.identity.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vg.identity.BaseIntegrationTest;
import vg.identity.entity.IdentityApplicationUserClaimEntity;
import vg.identity.model.IdentityApplicationUserPrincipal;
import vg.identity.rest.v1.IdentityApplicationApiRestClient;
import vg.unique.id.model.UniqueId;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityApplicationRestClientIntegrationTest extends BaseIntegrationTest {
    @Autowired
    private IdentityApplicationApiRestClient restClient;

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
    void authenticateTelegram_whenInitDataIsValid_returnsStableApplicationUserPrincipal() throws Exception {
        var botToken = "123456:integration-test-token";
        var application = createApplicationWithApiKey("{\"token\":\"" + botToken + "\"}");
        var initData = initData(botToken, "{\"id\":42,\"first_name\":\"John\"}");

        var first = restClient.authenticateTelegram(initData);
        assertThat(first).isPresent();
        assertThat(first.orElseThrow().applicationUniqueId()).isEqualTo(new UniqueId(application.uniqueId()).toString());
        assertThat(first.orElseThrow().identityUserUniqueId()).isNotBlank();
        assertThat(first.orElseThrow().claimsByScope()).isEmpty();

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

        var second = restClient.authenticateTelegram(initData);
        var third = restClient.authenticateTelegram(initData);

        assertThat(second.orElseThrow().identityUserUniqueId()).isEqualTo(first.orElseThrow().identityUserUniqueId());
        assertThat(second.orElseThrow().claimsByScope())
                .containsExactly(Map.entry(IdentityApplicationUserPrincipal.PERMISSIONS_SCOPE, Set.of("orders.read")));
        assertThat(third).contains(second.orElseThrow());
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
