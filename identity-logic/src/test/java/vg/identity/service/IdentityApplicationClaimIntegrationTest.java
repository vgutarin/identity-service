package vg.identity.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vg.identity.BaseIntegrationTest;
import vg.identity.entity.IdentityApplicationUserClaimEntity;
import vg.identity.entity.IdentityWorkspaceEntity;
import vg.identity.entity.IdentityWorkspaceScopeClaimDictionaryEntity;
import vg.identity.model.IdentityApiKeyPrincipal;
import vg.identity.model.IdentityApplicationUserPrincipal;
import vg.unique.id.model.UniqueId;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityApplicationClaimIntegrationTest extends BaseIntegrationTest {
    @Autowired
    private IdentityApplicationApi applicationApi;
    @Autowired
    private AuthorityChecker authorityChecker;
    @Autowired
    private EncryptionService encryptionService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void applicationClaims_areScopedToTheirApplicationAndNeverGrantPlatformPermissions() throws Exception {
        var applicationA = createTelegramApplication("123456:application-a-token");
        var applicationB = createTelegramApplication("123456:application-b-token");
        authenticateAs(applicationA);

        var initData = initData(applicationA.botToken(), "{\"id\":42,\"first_name\":\"John\"}");
        applicationApi.authenticateTelegram(initData).orElseThrow();
        var user = channelRepository.findByChannelTypeAndChannelUserIdHash(
                        vg.identity.model.IdentityChannelType.TELEGRAM_USER,
                        encryptionService.hashCaseSensitive("42")
                )
                .map(channel -> channel.getIdentityUser())
                .orElseThrow();

        grantPermission(applicationB.uniqueId(), user.getUniqueId(), "app.update");

        assertThat(applicationApi.authenticateTelegram(initData).orElseThrow().claimsByScope()).isEmpty();

        grantPermission(applicationA.uniqueId(), user.getUniqueId(), "app.update");

        assertThat(applicationApi.authenticateTelegram(initData).orElseThrow().claimsByScope())
                .containsExactly(Map.entry(IdentityApplicationUserPrincipal.PERMISSIONS_SCOPE, Set.of("app.update")));
        assertThat(authorityChecker.hasAuthority(new UniqueId(applicationA.uniqueId()), "app.update")).isFalse();
    }

    private void grantPermission(long applicationUniqueId, long identityUserUniqueId, String permissionName) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var application = applicationRepository.findById(applicationUniqueId).orElseThrow();
            var workspace = application.getWorkspace();
            applicationUserClaimRepository.save(IdentityApplicationUserClaimEntity.builder()
                    .application(application)
                    .identityUser(userRepository.getReferenceById(identityUserUniqueId))
                    .scope(getDictionaryEntry(workspace, IdentityApplicationUserPrincipal.PERMISSIONS_SCOPE))
                    .claim(getDictionaryEntry(workspace, permissionName))
                    .build());
        });
    }

    private IdentityWorkspaceScopeClaimDictionaryEntity getDictionaryEntry(IdentityWorkspaceEntity workspace, String value) {
        var blindIndex = encryptionService.hashCaseSensitive(value);
        return scopeClaimDictionaryRepository
                .findByWorkspace_UniqueIdAndNameBlindIndex(workspace.getUniqueId(), blindIndex)
                .orElseGet(() -> scopeClaimDictionaryRepository.save(IdentityWorkspaceScopeClaimDictionaryEntity.builder()
                        .workspace(workspace)
                        .name(value)
                        .nameBlindIndex(blindIndex)
                        .build()));
    }

    private void authenticateAs(ApplicationFixture application) {
        var context = SecurityContextHolder.createEmptyContext();
        var principal = new IdentityApiKeyPrincipal(new UniqueId(application.uniqueId()), application.uri());
        context.setAuthentication(new PreAuthenticatedAuthenticationToken(principal, null, List.of()));
        SecurityContextHolder.setContext(context);
    }

    private ApplicationFixture createTelegramApplication(String botToken) {
        var application = createApplication(createWorkspace(), "{\"token\":\"" + botToken + "\"}");
        var uri = principalRepository.findById(new UniqueId(application.getUniqueId())).orElseThrow().getName();
        return new ApplicationFixture(application.getUniqueId(), uri, botToken);
    }

    private static String initData(String botToken, String userJson) throws Exception {
        var parameters = Map.of(
                "auth_date", String.valueOf(Instant.now().getEpochSecond()),
                "query_id", "application-authorization-test",
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

    private record ApplicationFixture(long uniqueId, String uri, String botToken) {
    }
}
