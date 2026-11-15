package vg.identity.model;

import lombok.Builder;

import java.util.UUID;

/** A plain-text submission with a required deduplication key. Identity selects the delivery channel. */
@Builder
public record IdentityApplicationMessageRequest(MessageContent content, UUID idempotencyKey) {
    public IdentityApplicationMessageRequest {
        if (content == null) {
            throw new IllegalArgumentException("Message content is required");
        }
        if (idempotencyKey == null) {
            throw new IllegalArgumentException("Idempotency key is required");
        }
    }
}
