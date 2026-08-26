package vg.identity.model;

import lombok.Builder;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The consent-gated principal returned when resolving Telegram sign-in data for one registered
 * application. It is OIDC-claim-compatible and spans two states, distinguished by {@link #consentGiven}:
 *
 * <ul>
 *   <li><b>Pre-consent (provisional)</b> — no user is stored: {@link #sub} is {@code null},
 *       {@link #permissions} is empty, {@code consentGiven} is {@code false}. {@link #name} is still
 *       present (derived from the Telegram profile).</li>
 *   <li><b>Established</b> — a user exists: {@link #sub} is a stable, non-null identifier,
 *       {@link #permissions} holds the caller-application-scoped permissions, {@code consentGiven}
 *       is {@code true}.</li>
 * </ul>
 *
 * <p>OIDC claim mapping: {@link #sub} → {@code sub}, {@link #name} → {@code name}. Additional optional
 * OIDC claims (e.g. {@code locale}) are intentionally <b>not</b> fields yet — they are reserved for a
 * future, additive change and, being optional, can be added without breaking existing consumers.
 * {@link #permissions} and {@link #consentGiven} are non-standard fields and never reuse an OIDC
 * standard claim name.</p>
 *
 * <p>Invariant: {@code consentGiven == (sub != null)} — a non-null {@code sub} always means consent was
 * given, and its absence always means consent is pending.</p>
 */
@Builder
public record IdentityApplicationUserPrincipal(
        String sub,
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
