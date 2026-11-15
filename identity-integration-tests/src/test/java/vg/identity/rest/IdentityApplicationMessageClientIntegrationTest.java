package vg.identity.rest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import vg.identity.model.IdentityApplicationMessageRequest;
import vg.identity.model.IdentityMessageStatus;
import vg.identity.model.MessageContent;
import vg.identity.rest.v1.IdentityApplicationApiRestClient;
import vg.unique.id.model.UniqueId;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/** Verifies the typed client's JSON contract with the host application's JSON runtime. */
class IdentityApplicationMessageClientIntegrationTest {
    private MockRestServiceServer server;
    private IdentityApplicationApiRestClient client;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl("https://identity.example.test")
                .defaultHeader(IdentityApplicationApiRestClient.API_KEY_HEADER, "test-api-key");
        server = MockRestServiceServer.bindTo(builder).build();
        client = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(builder.build()))
                .build().createClient(IdentityApplicationApiRestClient.class);
    }

    @Test
    void submitMessage_whenContentIsProvided_postsNestedCompactJsonAndPreservesCallerKey() {
        var user = new UniqueId(84L);
        var message = new UniqueId(100L);
        var key = UUID.fromString("a841d4c4-1bc4-4b2f-aecf-1a4dc2ddfd92");
        var request = IdentityApplicationMessageRequest.builder()
                .content(MessageContent.builder().text(" <b>Hello</b> {BOT_URI} 🙂 ").build()).idempotencyKey(key).build();
        server.expect(requestTo("https://identity.example.test/api/v1/applications/me/users/" + user + "/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(IdentityApplicationApiRestClient.API_KEY_HEADER, "test-api-key"))
                .andExpect(content().json("""
                        {"content":{"t":" <b>Hello</b> {BOT_URI} 🙂 "},"idempotencyKey":"a841d4c4-1bc4-4b2f-aecf-1a4dc2ddfd92"}
                        """, JsonCompareMode.STRICT))
                .andRespond(withStatus(HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"messageUniqueId\":\"" + message + "\",\"userUniqueId\":\"" + user + "\",\"status\":\"QUEUED\"}"));

        var result = client.submitMessage(user, request);

        assertThat(result.replayed()).isFalse();
        assertThat(result.message().messageUniqueId()).isEqualTo(message);
        assertThat(result.message().status()).isEqualTo(IdentityMessageStatus.QUEUED);
        server.verify();
    }
}
