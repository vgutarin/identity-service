package vg.identity.rest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vg.identity.BaseIntegrationTest;
import vg.identity.entity.IdentityApplicationUserEntityId;
import vg.identity.model.IdentityApplicationUserMetadata;
import vg.identity.model.TelegramAuthenticationRequest;
import vg.identity.model.principal.IdentityInternalApiKeyPrincipal;
import vg.identity.rest.v1.IdentityApplicationApiRestClient;
import vg.identity.service.IdentityApplicationApiService;
import vg.unique.id.model.UniqueId;

import java.net.URI;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Constitution Principle VI: the embedded and remote implementations of {@code IdentityApplicationApi} must
 * behave equivalently. Runs each public operation through the embedded {@link IdentityApplicationApiService}
 * and the remote {@link IdentityApplicationApiRestClient} for the same application and asserts identical
 * results. Covers {@code me()} and {@code authenticateTelegram} (pre-consent and established).
 */
class EmbeddedVsRemoteConformanceTest extends BaseIntegrationTest {

    private static final String BOT_TOKEN = "123456:conformance-test-token";
    private static final String TELEGRAM_USER = "{\"id\":42,\"first_name\":\"John\"}";

    @Autowired
    private IdentityApplicationApiService embeddedApi;
    @Autowired
    private IdentityRestClient identityRestClient;

    private IdentityApplicationApiRestClient remoteApi;

    @BeforeEach
    void setUpRemoteApi() {
        var properties = new IdentityRestClientProperties();
        properties.setBaseUrl("http://localhost:" + serverPort);
        properties.setApiKey(restClientApiKey());
        remoteApi = identityRestClient.identityApplicationApiRestClient(properties);
    }

    @Test
    void me_embeddedAndRemote_returnEquivalentMetadata() {
        var application = createApplicationWithApiKey();

        var remote = remoteApi.me();
        var embedded = runAsApplication(application, embeddedApi::me);

        assertThat(embedded).isEqualTo(remote);
        // T022: the retyped application principal carries typed, domain-native fields (not OIDC claims).
        assertThat(remote.applicationUniqueId()).isEqualTo(new UniqueId(application.uniqueId()));
        assertThat(remote.workspaceUniqueId()).isEqualTo(new UniqueId(application.workspaceUniqueId()));
        assertThat(remote.uri()).isEqualTo(URI.create(application.uri()));
    }

    @Test
    void authenticateTelegram_preConsent_embeddedAndRemote_returnEquivalentProvisionalPrincipal() throws Exception {
        var application = createApplicationWithApiKey("{\"token\":\"" + BOT_TOKEN + "\"}");
        var request = TelegramAuthenticationRequest.builder()
                .telegramInitData(telegramInitData(BOT_TOKEN, TELEGRAM_USER))
                .consentToKeepPersonalData(false)
                .build();

        var remote = remoteApi.authenticateTelegram(request);
        var embedded = runAsApplication(application, () -> embeddedApi.authenticateTelegram(request));

        assertThat(embedded).isEqualTo(remote);
        assertThat(remote.orElseThrow().userUniqueId()).isNull();
        assertThat(remote.orElseThrow().consentGiven()).isFalse();
    }

    @Test
    void authenticateTelegram_established_embeddedAndRemote_returnEquivalentEstablishedPrincipal() throws Exception {
        var application = createApplicationWithApiKey("{\"token\":\"" + BOT_TOKEN + "\"}");
        var request = TelegramAuthenticationRequest.builder()
                .telegramInitData(telegramInitData(BOT_TOKEN, TELEGRAM_USER))
                .consentToKeepPersonalData(true)
                .build();

        // Remote call establishes the user; the embedded call must resolve the same established principal.
        var remote = remoteApi.authenticateTelegram(request);
        var embedded = runAsApplication(application, () -> embeddedApi.authenticateTelegram(request));

        assertThat(embedded).isEqualTo(remote);
        assertThat(remote.orElseThrow().userUniqueId()).isNotNull();
        assertThat(remote.orElseThrow().consentGiven()).isTrue();
    }

    @Test
    void telegramPhone_embeddedAndRemote_useTheSameDisclosureAndRevocationBoundary() throws Exception {
        var application = createApplicationWithApiKey("{\"token\":\"" + BOT_TOKEN + "\"}");
        var request = TelegramAuthenticationRequest.builder()
                .telegramInitData(telegramInitData(BOT_TOKEN, TELEGRAM_USER))
                .consentToKeepPersonalData(true)
                .build();
        var userUniqueId = remoteApi.authenticateTelegram(request).orElseThrow().userUniqueId();
        var channel = channelRepository.findByChannelTypeAndChannelUserIdHash(
                        vg.identity.model.IdentityChannelType.TELEGRAM_USER,
                        encryptionService.hashCaseSensitive("42")
                )
                .orElseThrow();
        channel.setPayload("{\"id\":42,\"first_name\":\"John\",\"phoneNumber\":\"+14155552671\"}");
        channelRepository.saveAndFlush(channel);
        var membershipId = new IdentityApplicationUserEntityId();
        membershipId.setApplication(application.uniqueId());
        membershipId.setIdentityUser(channel.getIdentityUser().getUniqueId());
        var membership = applicationUserRepository.findById(membershipId).orElseThrow();
        membership.setMetadata(new IdentityApplicationUserMetadata(new int[]{0}));
        applicationUserRepository.saveAndFlush(membership);

        var remote = remoteApi.findTelegramPhone(userUniqueId);
        var embedded = runAsApplication(application, () -> embeddedApi.findTelegramPhone(userUniqueId));

        assertThat(embedded).isEqualTo(remote);

        runAsApplication(application, () -> {
            embeddedApi.revokeTelegramPhone(userUniqueId);
            return null;
        });

        assertThat(remoteApi.findTelegramPhone(userUniqueId)).isEmpty();
    }

    private <T> T runAsApplication(TestApplication application, Supplier<T> action) {
        var principal = new IdentityInternalApiKeyPrincipal(new UniqueId(application.uniqueId()), application.uri());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
