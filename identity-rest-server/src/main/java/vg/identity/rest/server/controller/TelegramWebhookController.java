package vg.identity.rest.server.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vg.identity.model.TelegramWebhookUpdate;
import vg.identity.rest.TelegramWebhookEndpoint;
import vg.identity.service.TelegramPhoneWebhookService;
import vg.unique.id.model.UniqueId;

/** Public Telegram ingress. Authentication is exclusively the Telegram webhook secret header. */
@RestController
@RequestMapping(TelegramWebhookEndpoint.BASE_PATH)
@RequiredArgsConstructor
@ConditionalOnBooleanProperty("identity.rest.api.enabled")
public class TelegramWebhookController {
    static final String SECRET_HEADER = "X-Telegram-Bot-Api-Secret-Token";

    private final TelegramPhoneWebhookService telegramPhoneWebhookService;

    @PostMapping("/{applicationUniqueId}")
    public ResponseEntity<Void> receive(
            @PathVariable String applicationUniqueId,
            @RequestHeader(value = SECRET_HEADER, required = false) String webhookSecret,
            @RequestBody TelegramWebhookUpdate update
    ) {
        final UniqueId parsedApplicationUniqueId;
        try {
            parsedApplicationUniqueId = UniqueId.parse(applicationUniqueId);
        } catch (IllegalArgumentException invalidApplicationUniqueId) {
            return ResponseEntity.noContent().build();
        }
        telegramPhoneWebhookService.accept(parsedApplicationUniqueId, webhookSecret, update);
        // A neutral 204 prevents retries from revealing which validation check rejected an update.
        return ResponseEntity.noContent().build();
    }
}
