package vg.identity.model;

import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A user authenticated for one registered identity application.
 *
 * <p>{@code identityUserUniqueId} is the immutable identity subject (OIDC {@code sub}).
 * Claims are application-scoped. The initial {@link #PERMISSIONS_SCOPE permissions} scope contains
 * unfolded permission strings.</p>
 */
public record IdentityApplicationUserPrincipal(
        String applicationUniqueId,
        String identityUserUniqueId,
        Map<String, Set<String>> claimsByScope
) {
    public static final String PERMISSIONS_SCOPE = "permissions";

    public IdentityApplicationUserPrincipal {
        var copiedClaimsByScope = new LinkedHashMap<String, Set<String>>();
        if (claimsByScope != null) {
            claimsByScope.forEach((scope, claims) -> {
                if (scope == null || scope.isBlank()) {
                    throw new IllegalArgumentException("Claim scope must not be blank");
                }
                copiedClaimsByScope.put(scope, claims == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(claims)));
            });
        }
        claimsByScope = Map.copyOf(copiedClaimsByScope);
    }
}
