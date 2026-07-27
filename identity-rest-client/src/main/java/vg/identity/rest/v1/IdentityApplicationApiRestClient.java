package vg.identity.rest.v1;

import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import vg.identity.model.AuthenticatedIdentityApplication;
import vg.identity.service.IdentityApplicationApi;

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
}
