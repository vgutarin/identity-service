package vg.identity.service;

import vg.identity.IdentityActionTokenProperties;

import java.net.URI;
import java.util.UUID;

/**
 * Default {@link IdentityActionLinkBuilder} used outside the Vaadin frontend (tests, non-web callers). Registered by
 * {@link vg.identity.IdentityLogicConfig} only when no other {@code ActionLinkBuilder} bean is present.
 * <p>
 * The confirmation link it produces is whatever {@link IdentityActionTokenProperties#getVerifyEmailBaseUrl()}
 * defines — host-relative by default, since {@code identity-logic} does not know the service's external
 * public origin. The Vaadin frontend supplies an implementation that builds an absolute URL.
 */
public class IdentityActionLinkBuilderDefault implements IdentityActionLinkBuilder {

    private final IdentityActionTokenProperties properties;

    public IdentityActionLinkBuilderDefault(IdentityActionTokenProperties properties) {
        this.properties = properties;
    }

    @Override
    public URI confirmationEmailUri(UUID actionId) {
        return URI.create(properties.getVerifyEmailBaseUrl() + actionId);
    }
}
