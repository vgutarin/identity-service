package vg.identity.rest.v1;

import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import vg.identity.model.IdentityApplicationMessage;
import vg.identity.model.IdentityMessageSubmission;
import vg.identity.model.IdentityApplicationMessageRequest;
import vg.identity.model.IdentityApplicationPrincipal;
import vg.identity.model.IdentityApplicationTelegramPhone;
import vg.identity.model.IdentityApplicationUserPrincipal;
import vg.identity.model.TelegramAuthenticationRequest;
import vg.identity.service.IdentityApplicationApi;
import vg.unique.id.model.UniqueId;

import java.util.Optional;

/**
 * Client for API-key-authenticated application endpoints.
 *
 * <p>The configured API key is sent with every request.</p>
 */
@HttpExchange("/api/v1/applications")
public interface IdentityApplicationApiRestClient extends IdentityApplicationApi {
    String API_KEY_HEADER = "X-VG-Identity-API-Key";

    @Override
    @GetExchange("/me")
    IdentityApplicationPrincipal me();

    @Override
    @PostExchange(url = "/me/authentications/telegram", contentType = MediaType.APPLICATION_JSON_VALUE)
    Optional<IdentityApplicationUserPrincipal> authenticateTelegram(@RequestBody TelegramAuthenticationRequest request);

    @GetExchange("/me/users/{userUniqueId}/telegram-phone")
    Optional<IdentityApplicationTelegramPhone> findTelegramPhoneByString(@PathVariable String userUniqueId);

    @DeleteExchange("/me/users/{userUniqueId}/telegram-phone")
    void revokeTelegramPhoneByString(@PathVariable String userUniqueId);

    @Override
    default Optional<IdentityApplicationTelegramPhone> findTelegramPhone(UniqueId userUniqueId) {
        return findTelegramPhoneByString(userUniqueId.toString());
    }

    @Override
    default void revokeTelegramPhone(UniqueId userUniqueId) {
        revokeTelegramPhoneByString(userUniqueId.toString());
    }

    /** Submits typed content ({@code content.t}) and the caller's required key; retries reuse that key. */
    @PostExchange(url = "/me/users/{userUniqueId}/messages", contentType = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<IdentityApplicationMessage> submitMessageByString(
            @PathVariable String userUniqueId,
            @RequestBody IdentityApplicationMessageRequest request
    );

    @Override
    default IdentityMessageSubmission submitMessage(UniqueId userUniqueId, IdentityApplicationMessageRequest request) {
        var response = submitMessageByString(userUniqueId.toString(), request);
        var status = response.getStatusCode().value();
        if (status != HttpStatus.ACCEPTED.value() && status != HttpStatus.OK.value()) {
            throw new RestClientException("Unexpected message submission status: " + status);
        }
        var message = response.getBody();
        if (message == null) {
            throw new RestClientException("Message submission response must contain a message");
        }
        return IdentityMessageSubmission.builder().message(message).replayed(status == HttpStatus.OK.value()).build();
    }

    @GetExchange("/me/messages/{messageUniqueId}")
    Optional<IdentityApplicationMessage> findMessageByString(@PathVariable String messageUniqueId);

    @Override
    default Optional<IdentityApplicationMessage> findMessage(UniqueId messageUniqueId) {
        try {
            return findMessageByString(messageUniqueId.toString());
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }
}
