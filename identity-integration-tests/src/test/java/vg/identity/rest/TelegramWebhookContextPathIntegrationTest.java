package vg.identity.rest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import vg.identity.BaseIntegrationTest;
import vg.identity.model.TelegramAuthenticationRequest;
import vg.identity.rest.v1.IdentityApplicationApiRestClient;
import vg.identity.service.TelegramWebhookSecretService;
import vg.unique.id.model.UniqueId;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "server.servlet.context-path=/identity")
class TelegramWebhookContextPathIntegrationTest extends BaseIntegrationTest {
    @Autowired private IdentityRestClient identityRestClient;
    @Autowired private TelegramWebhookSecretService secretService;

    private IdentityApplicationApiRestClient restClient;
    private String baseUrl;

    @BeforeEach
    void setUpClient() {
        baseUrl = "http://localhost:" + serverPort + "/identity";
        var properties = new IdentityRestClientProperties();
        properties.setBaseUrl(baseUrl);
        properties.setApiKey(restClientApiKey());
        restClient = identityRestClient.identityApplicationApiRestClient(properties);
    }

    @Test
    void webhook_withContextPathAndValidSecret_recordsPhoneWithoutApiKey() throws Exception {
        var botToken = "123456:integration-test-token";
        var application = createApplicationWithApiKey("{\"telegramBotId\":123456,\"token\":\"" + botToken + "\"}");
        var applicationId = new UniqueId(application.uniqueId());
        var userId = restClient.authenticateTelegram(TelegramAuthenticationRequest.builder()
                        .telegramInitData(telegramInitData(botToken, "{\"id\":42,\"first_name\":\"John\"}"))
                        .consentToKeepPersonalData(true)
                        .build())
                .orElseThrow().userUniqueId();

        var response = sendContact(applicationId, secretService.secretFor(applicationId, 123456L));

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(restClient.findTelegramPhone(userId)).map(phone -> phone.phoneNumber()).contains("+14155552671");
        assertThat(restClient.me().applicationUniqueId()).isEqualTo(applicationId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "invalid-secret"})
    void webhook_withContextPathAndMissingOrInvalidSecret_acknowledgesWithoutChangingState(String secret) throws Exception {
        var application = createApplicationWithApiKey("{\"telegramBotId\":123456,\"token\":\"test-token\"}");

        var response = sendContact(new UniqueId(application.uniqueId()), secret);

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(applicationUserRepository.count()).isZero();
        assertThat(channelRepository.count()).isZero();
        assertThat(userRepository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/applications/me", "/api/v1/telegram/webhooks-other/test"})
    void protectedRoutes_withContextPathAndNoApiKey_remainUnauthorized(String path) throws Exception {
        var response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build(),
                HttpResponse.BodyHandlers.discarding());

        assertThat(response.statusCode()).isEqualTo(401);
    }

    private HttpResponse<Void> sendContact(UniqueId applicationId, String secret) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(baseUrl + TelegramWebhookEndpoint.path(applicationId)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"message":{"from":{"id":42},"chat":{"type":"private"},"contact":{"phone_number":"+1 415 555 2671","user_id":42}}}
                        """));
        if (!secret.isEmpty()) {
            request.header("X-Telegram-Bot-Api-Secret-Token", secret);
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.discarding());
    }
}
