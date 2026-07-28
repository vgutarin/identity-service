package vg.identity.model;

import vg.unique.id.model.UniqueId;

import java.time.Instant;

/**
 * An identity user that has authenticated for one application, as seen by workspace administrators managing
 * that application's users.
 */
public record IdentityApplicationUser(
        UniqueId uniqueId,
        Instant firstAuthenticatedAt,
        Instant lastAuthenticatedAt
) {
}
