package vg.identity.model;

import lombok.Builder;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;
import vg.unique.id.model.UniqueId;

/** Ephemeral delivery data. Never persisted or serialized into a queue command. */
@Value
@Builder
@Accessors(fluent = true)
@ToString(onlyExplicitlyIncluded = true)
public class PreparedIdentityMessage {
    UniqueId messageUniqueId;
    String token;
    long chatId;
    String text;
    IdentityMessageFailureCode failureCode;
}
