package vg.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TelegramApiClientTest {

    private MockRestServiceServer server;
    private TelegramApiClient client;

    @BeforeEach
    void setUp() {
        var restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        client = new TelegramApiClient(restClientBuilder, 7);
    }

    @Test
    void sendMessage_whenTelegramAcknowledgesMessage_postsPlainTextAndMapsAcknowledgement() {
        server.expect(requestTo("https://api.telegram.org/bot123456%3Atoken/sendMessage"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string("{\"chat_id\":42,\"text\":\"<b>Hello</b> 🙂\"}"))
                .andRespond(withSuccess("{\"ok\":true,\"result\":{\"message_id\":7,\"chat\":{\"id\":42}}}", MediaType.APPLICATION_JSON));

        var response = client.sendMessage("123456:token", 42L, "<b>Hello</b> 🙂");

        assertThat(response.ok()).isTrue();
        assertThat(response.result().messageId()).isEqualTo(7L);
        server.verify();
    }

    @Test
    void getMe_whenTelegramReturnsUser_mapsResponse() {
        server.expect(requestTo("https://api.telegram.org/bot123456%3Atoken/getMe"))
                .andRespond(withSuccess("""
                        {
                          "ok": true,
                          "result": {
                            "id": 123456,
                            "is_bot": true,
                            "first_name": "Test",
                            "username": "test_bot"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        var response = client.getMe("123456:token");

        assertThat(response.ok()).isTrue();
        assertThat(response.result().id()).isEqualTo(123456L);
        assertThat(response.result().bot()).isTrue();
        assertThat(response.result().firstName()).isEqualTo("Test");
        assertThat(response.result().username()).isEqualTo("test_bot");
        server.verify();
    }

    @Test
    void setWebhook_postsTheCallbackSecretTokenAndConfiguredMaxConnections() {
        server.expect(requestTo("https://api.telegram.org/bot123456%3Atoken/setWebhook"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "url": "https://identity.example.com/api/v1/telegram/webhooks/42",
                          "secret_token": "per-application-secret",
                          "max_connections": 7
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "ok": true,
                          "description": "Webhook was set"
                        }
                        """, MediaType.APPLICATION_JSON));

        var response = client.setWebhook(
                "123456:token",
                "https://identity.example.com/api/v1/telegram/webhooks/42",
                "per-application-secret"
        );

        assertThat(response.ok()).isTrue();
        assertThat(response.description()).isEqualTo("Webhook was set");
        server.verify();
    }

    @Test
    void deleteWebhook_postsToTheBotWebhookEndpoint() {
        server.expect(requestTo("https://api.telegram.org/bot123456%3Atoken/deleteWebhook"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "ok": true,
                          "description": "Webhook was deleted"
                        }
                        """, MediaType.APPLICATION_JSON));

        var response = client.deleteWebhook("123456:token");

        assertThat(response.ok()).isTrue();
        assertThat(response.description()).isEqualTo("Webhook was deleted");
        server.verify();
    }

    @Test
    void constructor_whenWebhookMaxConnectionsIsOutsideTelegramRange_failsFast() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TelegramApiClient(RestClient.builder(), 0))
                .withMessageContaining("identity.telegram.webhook.max-connections")
                .withMessageContaining("between 1 and 100");
    }

    @Test
    void getWebhookInfo_mapsTheUrlWithoutRequiringOtherTelegramFields() {
        server.expect(requestTo("https://api.telegram.org/bot123456%3Atoken/getWebhookInfo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"ok":true,"result":{"url":"https://identity.example.test/webhook","pending_update_count":0}}
                        """, MediaType.APPLICATION_JSON));

        var response = client.getWebhookInfo("123456:token");

        assertThat(response.ok()).isTrue();
        assertThat(response.result().url()).isEqualTo("https://identity.example.test/webhook");
        server.verify();
    }

    @Test
    void getWebhookInfo_preservesAnEmptyUrlForAnUnconfiguredWebhook() {
        server.expect(requestTo("https://api.telegram.org/bot123456%3Atoken/getWebhookInfo"))
                .andRespond(withSuccess("{\"ok\":true,\"result\":{\"url\":\"\"}}", MediaType.APPLICATION_JSON));

        assertThat(client.getWebhookInfo("123456:token").result().url()).isEmpty();
        server.verify();
    }
}
