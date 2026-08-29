package vg.identity.model;

import lombok.Builder;
import vg.unique.id.model.UniqueId;

import java.time.Instant;

/**
 * An identity user that has authenticated for one application, as seen by workspace administrators managing
 * that application's users.
 */
@Builder
public record IdentityApplicationUser(
        UniqueId uniqueId,
        Instant firstAuthenticatedAt,
        Instant lastAuthenticatedAt,
        String displayName
) {
}
