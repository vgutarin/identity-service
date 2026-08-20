package vg.identity.service;

import java.net.URI;

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
     * {@code https://identity.vg/verify/email/<actionKey>}.
     */
    URI confirmationEmailUri(String actionKey);

    /**
     * Link that opens the password-reset page for the given action token, e.g.
     * {@code https://identity.vg/reset/password/<actionKey>}.
     * <p>
     * Like {@link #confirmationEmailUri(String)} this is deliberately abstract: the host-relative
     * {@link IdentityActionLinkBuilderDefault} serves tests/non-web callers, while the Vaadin frontend
     * supplies an absolute link. A missing implementation is a compile error, not a silent host-relative
     * link in an email.
     */
    URI resetPasswordUri(String actionKey);
}
