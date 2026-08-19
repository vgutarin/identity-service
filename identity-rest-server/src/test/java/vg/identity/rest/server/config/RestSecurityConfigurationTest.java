package vg.identity.rest.server.config;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import vg.identity.rest.server.security.ApiKeyAuthenticationFilter;

import static org.assertj.core.api.Assertions.assertThat;

class RestSecurityConfigurationTest {

    /**
     * The API-key filter must NOT be auto-registered as a global servlet filter, or it would intercept every
     * request (including a co-hosted Vaadin UI) and reject it for lacking the API key. It participates only in
     * the {@code /api/**} security chain.
     */
    @Test
    void apiKeyFilterIsNotRegisteredAsGlobalServletFilter() {
        var filter = Mockito.mock(ApiKeyAuthenticationFilter.class);

        var registration = new RestSecurityConfiguration().apiKeyAuthenticationFilterRegistration(filter);

        assertThat(registration.isEnabled()).isFalse();
    }
}
