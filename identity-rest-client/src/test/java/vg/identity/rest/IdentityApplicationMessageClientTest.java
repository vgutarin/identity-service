package vg.identity.rest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import vg.identity.model.IdentityApplicationMessage;
import vg.identity.model.IdentityApplicationMessageRequest;
import vg.identity.model.MessageContent;
import vg.identity.model.IdentityMessageStatus;
import vg.identity.rest.v1.IdentityApplicationApiRestClient;
import vg.unique.id.model.UniqueId;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityApplicationMessageClientTest {
    private static final UniqueId USER = new UniqueId(84L);
    private static final UniqueId MESSAGE = new UniqueId(100L);
    @Mock(answer = Answers.CALLS_REAL_METHODS)
    private IdentityApplicationApiRestClient client;

    @Test
    void submitMessage_whenAccepted_returnsNewSubmission() {
        var request = request();
        var message = message(IdentityMessageStatus.QUEUED);
        when(client.submitMessageByString(USER.toString(), request)).thenReturn(ResponseEntity.accepted().body(message));

        var result = client.submitMessage(USER, request);

        assertThat(result.replayed()).isFalse();
        assertThat(result.message()).isSameAs(message);
    }

    @Test
    void submitMessage_whenHttpStatusIsOkAndMessageIsQueued_returnsReplayedSubmission() {
        var key = UUID.randomUUID();
        var request = request(key);
        var message = message(IdentityMessageStatus.QUEUED);
        when(client.submitMessageByString(USER.toString(), request)).thenReturn(ResponseEntity.ok(message));

        var result = client.submitMessage(USER, request);

        assertThat(result.replayed()).isTrue();
        assertThat(result.message()).isSameAs(message);
    }

    @Test
    void submitMessage_whenHttpStatusIsOkAndMessageIsSent_preservesFinalStateInReplay() {
        var key = UUID.randomUUID();
        var request = request(key);
        var message = message(IdentityMessageStatus.SENT);
        when(client.submitMessageByString(USER.toString(), request)).thenReturn(ResponseEntity.ok(message));
        var result = client.submitMessage(USER, request);
        assertThat(result.replayed()).isTrue();
        assertThat(result.message().status()).isEqualTo(IdentityMessageStatus.SENT);
    }

    @Test
    void submitMessage_whenAcceptedResponseHasNoBody_throwsRestClientException() {
        var request = request();
        when(client.submitMessageByString(USER.toString(), request)).thenReturn(ResponseEntity.accepted().build());
        assertThatThrownBy(() -> client.submitMessage(USER, request)).isInstanceOf(RestClientException.class)
                .hasMessage("Message submission response must contain a message");
    }

    @Test
    void submitMessage_whenSuccessfulStatusIsUnexpected_throwsRestClientException() {
        var request = request();
        when(client.submitMessageByString(USER.toString(), request))
                .thenReturn(ResponseEntity.status(HttpStatus.CREATED).body(message(IdentityMessageStatus.QUEUED)));
        assertThatThrownBy(() -> client.submitMessage(USER, request)).isInstanceOf(RestClientException.class)
                .hasMessage("Unexpected message submission status: 201");
    }

    @Test
    void submitMessage_whenHttpExchangeConflicts_propagatesHttpError() {
        var key = UUID.randomUUID();
        var request = request(key);
        var conflict = new HttpClientErrorException(HttpStatus.CONFLICT);
        when(client.submitMessageByString(USER.toString(), request)).thenThrow(conflict);
        assertThatThrownBy(() -> client.submitMessage(USER, request)).isSameAs(conflict);
    }

    private static IdentityApplicationMessageRequest request() {
        return request(UUID.randomUUID());
    }
    private static IdentityApplicationMessageRequest request(UUID key) {
        return IdentityApplicationMessageRequest.builder().content(MessageContent.builder().text("hello").build()).idempotencyKey(key).build();
    }
    private static IdentityApplicationMessage message(IdentityMessageStatus status) {
        return IdentityApplicationMessage.builder().messageUniqueId(MESSAGE).userUniqueId(USER).status(status).build();
    }
}
