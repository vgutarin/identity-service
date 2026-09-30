package vg.identity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import vg.identity.service.IdentityPublicUriBuilder;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "identity.service.public-url=https://identity.example.test/identity")
class IdentityPublicUriBuilderIntegrationTest extends BaseIntegrationTest {
    @Autowired
    private IdentityPublicUriBuilder publicUriBuilder;

    @Test
    void publicUriBuilder_usesTheApplicationConfiguredExternalOrigin() {
        assertThat(publicUriBuilder.build("/api/v1/telegram/webhooks/42").toString())
                .isEqualTo("https://identity.example.test/identity/api/v1/telegram/webhooks/42");
    }
}
