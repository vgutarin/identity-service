package vg.identity.model;

import lombok.Builder;
import vg.unique.id.model.UniqueId;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The consent-gated principal returned when resolving Telegram sign-in data for one registered
 * application. It spans two states, distinguished by {@link #consentGiven}:
 *
 * <ul>
 *   <li><b>Pre-consent (provisional)</b> — no user is stored: {@link #userUniqueId} is {@code null},
 *       {@link #permissions} is empty, {@code consentGiven} is {@code false}. {@link #name} is still
 *       present (derived from the Telegram profile).</li>
 *   <li><b>Established</b> — a user exists: {@link #userUniqueId} is a stable, non-null identifier,
 *       {@link #permissions} holds the caller-application-scoped permissions, {@code consentGiven}
 *       is {@code true}.</li>
 * </ul>
 *
 * <p>Identifiers use the system's {@link UniqueId} type with self-explaining names; the principal is
 * <b>not</b> OIDC-compatible and does not use OIDC standard claim names. A reserved additional-data
 * placeholder such as a preferred locale (domain-native name, e.g. {@code preferredLocale}) is
 * intentionally <b>not</b> a field yet — it is reserved for a future, additive change and, being
 * optional, can be added without breaking existing consumers. {@link #permissions} and
 * {@link #consentGiven} are ordinary fields.</p>
 *
 * <p>Invariant: {@code consentGiven == (userUniqueId != null)} — a non-null {@code userUniqueId} always
 * means consent was given, and its absence always means consent is pending.</p>
 */
@Builder
public record IdentityApplicationUserPrincipal(
        UniqueId userUniqueId,
        String name,
        Set<String> permissions,
        boolean consentGiven
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** Scope name under which the identity service stores a user's unfolded permission strings. */
    public static final String PERMISSIONS_SCOPE = "permissions";

    public IdentityApplicationUserPrincipal {
        permissions = permissions == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(permissions));
    }
}
