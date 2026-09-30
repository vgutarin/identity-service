package vg.identity.rest.v1;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import vg.identity.model.IdentityApplicationPrincipal;
import vg.identity.model.IdentityApplicationUserPrincipal;
import vg.identity.model.IdentityApplicationTelegramPhone;
import vg.identity.model.TelegramAuthenticationRequest;
import vg.identity.service.IdentityApplicationApi;

import java.util.Optional;
import vg.unique.id.model.UniqueId;

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
    Optional<IdentityApplicationTelegramPhone> findTelegramPhoneByString(
            @org.springframework.web.bind.annotation.PathVariable String userUniqueId
    );

    @DeleteExchange("/me/users/{userUniqueId}/telegram-phone")
    void revokeTelegramPhoneByString(@org.springframework.web.bind.annotation.PathVariable String userUniqueId);

    @Override
    default Optional<IdentityApplicationTelegramPhone> findTelegramPhone(UniqueId userUniqueId) {
        return findTelegramPhoneByString(userUniqueId.toString());
    }

    @Override
    default void revokeTelegramPhone(UniqueId userUniqueId) {
        revokeTelegramPhoneByString(userUniqueId.toString());
    }
}
