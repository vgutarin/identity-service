package vg.identity.model;

import vg.unique.id.model.UniqueId;

import java.net.URI;

/**
 * Safe metadata for the application represented by the current application principal.
 *
 * <p>Identifiers use the system's {@link UniqueId} type with self-explaining names and the application
 * location is a {@link URI}; the principal is <b>not</b> OIDC-compatible.</p>
 */
public record IdentityApplicationPrincipal(
        UniqueId applicationUniqueId,
        UniqueId workspaceUniqueId,
        String name,
        URI uri
) {
}
