package vg.identity.frontend.vaadin.service;

import com.vaadin.flow.router.RouteConfiguration;
import com.vaadin.flow.router.RouteParameters;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import vg.identity.frontend.vaadin.auth.IdentityUserEmailVerificationView;
import vg.identity.service.IdentityActionLinkBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * Frontend {@link IdentityActionLinkBuilder} that builds absolute links against this deployment's external public
 * origin ({@code identity.service.public-url}). Its presence suppresses the host-relative default builder
 * registered in {@code identity-logic}.
 * <p>
 * The confirmation link combines the public origin with the route Vaadin resolves for
 * {@link IdentityUserEmailVerificationView} (the id is a path parameter), keeping the route defined once —
 * on the view's {@code @Route} template.
 */
@Component
public class IdentityActionLinkBuilderVaadin implements IdentityActionLinkBuilder {

    private final String publicUrl;

    public IdentityActionLinkBuilderVaadin(@Value("${identity.service.public-url}") String publicUrl) {
        this.publicUrl = publicUrl;
    }

    @Override
    public URI confirmationEmailUri(UUID actionId) {
        var route = RouteConfiguration.forApplicationScope().getUrl(
                IdentityUserEmailVerificationView.class,
                new RouteParameters(IdentityUserEmailVerificationView.ID_PARAM, actionId.toString())
        );
        var base = StringUtils.trimTrailingCharacter(publicUrl, '/');
        return UriComponentsBuilder.fromUriString(base)
                .path("/" + route)
                .build()
                .toUri();
    }
}
