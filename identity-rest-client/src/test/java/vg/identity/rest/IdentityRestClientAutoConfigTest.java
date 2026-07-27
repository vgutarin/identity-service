package vg.identity.rest;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import vg.identity.rest.v1.IdentityApplicationApiRestClient;
import vg.identity.service.IdentityApplicationApi;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityRestClientAutoConfigTest {
    private static final String TEST_API_KEY = UUID.randomUUID().toString();

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IdentityRestClientAutoConfig.class));

    @Test
    void createsTypedClient_whenApiKeyIsConfigured() {
        contextRunner
                .withPropertyValues("vg.identity.rest-client.api-key=" + TEST_API_KEY)
                .run(context -> {
                    assertThat(context).hasSingleBean(IdentityApplicationApiRestClient.class);
                    assertThat(context).hasSingleBean(IdentityApplicationApi.class);
                });
    }

    @Test
    void failsStartup_whenApiKeyIsMissing() {
        contextRunner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasRootCauseMessage("Property 'vg.identity.rest-client.api-key' must not be blank");
        });
    }
}
