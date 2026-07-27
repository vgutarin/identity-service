package vg.identity.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Non-secret API-key metadata that may be shown to an application administrator.
 */
public record IdentityApiKey(
        UUID id,
        String label,
        Instant createdAt,
        Instant expiresAt,
        Instant revokedAt
) {
    public boolean isActiveAt(Instant instant) {
        return revokedAt == null && expiresAt.isAfter(instant);
    }
}
