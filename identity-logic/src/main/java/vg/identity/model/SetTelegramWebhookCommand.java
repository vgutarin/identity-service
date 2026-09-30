package vg.identity.model;

import lombok.Builder;
import vg.unique.id.model.UniqueId;

import java.util.Objects;

/** Configures the application's current bot; credentials are loaded when the command executes. */
@Builder
public record SetTelegramWebhookCommand(UniqueId applicationUniqueId) {
    public SetTelegramWebhookCommand {
        Objects.requireNonNull(applicationUniqueId, "applicationUniqueId is required");
    }
}
