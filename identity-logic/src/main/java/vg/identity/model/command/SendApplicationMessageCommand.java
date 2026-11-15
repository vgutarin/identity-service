package vg.identity.model.command;

import lombok.Builder;
import vg.unique.id.model.UniqueId;

import java.util.Objects;

@Builder
public record SendApplicationMessageCommand(UniqueId messageUniqueId) {
    public SendApplicationMessageCommand {
        Objects.requireNonNull(messageUniqueId, "messageUniqueId is required");
    }
}
