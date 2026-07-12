package vg.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TelegramApiClientTest {

    private MockRestServiceServer server;
    private TelegramApiClient client;

    @BeforeEach
    void setUp() {
        var restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        client = new TelegramApiClient(restClientBuilder);
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
}
