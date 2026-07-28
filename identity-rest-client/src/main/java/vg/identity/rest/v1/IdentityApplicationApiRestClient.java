package vg.identity.rest.v1;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import vg.identity.model.AuthenticatedIdentityApplication;
import vg.identity.model.IdentityApplicationUserPrincipal;
import vg.identity.service.IdentityApplicationApi;

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
    AuthenticatedIdentityApplication me();

    @Override
    @PostExchange(url = "/me/authentications/telegram", contentType = MediaType.TEXT_PLAIN_VALUE)
    Optional<IdentityApplicationUserPrincipal> authenticateTelegram(@RequestBody String initData);
}
