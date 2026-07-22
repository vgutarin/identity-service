package vg.identity.service;

import java.net.URI;
import java.util.UUID;

/**
 * Builds the external confirmation link embedded in identity action notifications.
 * <p>
 * The {@link IdentityActionLinkBuilderDefault default implementation} lives in {@code identity-logic} and produces a
 * host-relative link, which is enough for tests and non-web callers. It is registered (by
 * {@link vg.identity.IdentityLogicConfig}) only when no other {@code ActionLinkBuilder} bean is present, so
 * the {@code identity-frontend-vaadin} module can supply an implementation that prepends the service's
 * external public origin ({@code identity.service.public-url}), making the link absolute and clickable from
 * an email client.
 */
public interface IdentityActionLinkBuilder {

    /**
     * Link that opens the email-verification page for the given action token, e.g.
     * {@code https://identity.vg/verify/email/<actionId>}.
     */
    URI confirmationEmailUri(UUID actionId);
}
