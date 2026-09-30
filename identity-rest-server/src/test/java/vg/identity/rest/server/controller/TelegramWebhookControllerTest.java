package vg.identity.rest.server.controller;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import vg.identity.model.TelegramWebhookUpdate;
import vg.identity.service.TelegramPhoneWebhookService;
import vg.unique.id.model.UniqueId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

class TelegramWebhookControllerTest {

    @Test
    void receive_acknowledgesTelegramAndDelegatesWithoutRevealingValidationOutcome() {
        var service = Mockito.mock(TelegramPhoneWebhookService.class);
        var controller = new TelegramWebhookController(service);
        var update = new TelegramWebhookUpdate(null);

        var response = controller.receive(new UniqueId(42L).toString(), "secret", update);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        Mockito.verify(service).accept(new UniqueId(42L), "secret", update);
    }

    @Test
    void receive_whenApplicationUniqueIdIsMalformed_acknowledgesWithoutDelegating() {
        var service = Mockito.mock(TelegramPhoneWebhookService.class);
        var controller = new TelegramWebhookController(service);

        var response = controller.receive("not-an-application-id", "secret", new TelegramWebhookUpdate(null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verifyNoInteractions(service);
    }
}
