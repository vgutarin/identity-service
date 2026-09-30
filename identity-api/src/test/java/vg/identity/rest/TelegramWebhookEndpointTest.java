package vg.identity.rest;

import org.junit.jupiter.api.Test;
import vg.unique.id.model.UniqueId;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramWebhookEndpointTest {

    @Test
    void path_buildsThePublicWebhookRouteForTheApplicationUniqueId() {
        var applicationUniqueId = new UniqueId(42L);

        assertThat(TelegramWebhookEndpoint.BASE_PATH).isEqualTo("/api/v1/telegram/webhooks");
        assertThat(TelegramWebhookEndpoint.path(applicationUniqueId))
                .isEqualTo("/api/v1/telegram/webhooks/" + applicationUniqueId);
    }
}
