package vg.identity.model;

import lombok.Builder;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;

/** Unmanaged submission projection. Contains decrypted content and must not be logged. */
@Builder
@Value
@Accessors(fluent = true)
@ToString(onlyExplicitlyIncluded = true)
public class IdentityMessageSnapshot {
    IdentityApplicationMessage message;
    MessageContent content;
}
