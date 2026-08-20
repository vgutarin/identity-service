package vg.identity.frontend.vaadin.service;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.router.RouteConfiguration;
import com.vaadin.flow.router.RouteParameters;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import vg.identity.frontend.vaadin.auth.IdentityUserEmailVerificationView;
import vg.identity.frontend.vaadin.auth.PasswordResetView;
import vg.identity.service.IdentityActionLinkBuilder;

import java.net.URI;

/**
 * Frontend {@link IdentityActionLinkBuilder} that builds absolute links against this deployment's external public
 * origin ({@code identity.service.public-url}). Its presence suppresses the host-relative default builder
 * registered in {@code identity-logic}.
 * <p>
 * The links combine the public origin with the route Vaadin resolves for each view (the action key is a path
 * parameter), keeping each route defined once — on the view's {@code @Route} template, layout/prefix
 * composition included.
 * <p>
 * Resolution goes through Vaadin's own {@link RouteConfiguration#forRegistry(com.vaadin.flow.server.RouteRegistry)}
 * builder, using the application route registry captured at startup by {@link VaadinRouteRegistryProvider}.
 * That registry is request-independent, so a link can be built off a Vaadin request thread (e.g. a workspace
 * invitation or a queued email) — unlike {@code RouteConfiguration.forApplicationScope()}, which needs
 * {@code VaadinService.getCurrent()} and only works on a request thread.
 */
@org.springframework.stereotype.Component
public class IdentityActionLinkBuilderVaadin implements IdentityActionLinkBuilder {

    private final String publicUrl;
    private final transient VaadinRouteRegistryProvider routeRegistryProvider;

    public IdentityActionLinkBuilderVaadin(
            @Value("${identity.service.public-url}") String publicUrl,
            VaadinRouteRegistryProvider routeRegistryProvider) {
        this.publicUrl = publicUrl;
        this.routeRegistryProvider = routeRegistryProvider;
    }

    @Override
    public URI confirmationEmailUri(String actionKey) {
        return route(
                IdentityUserEmailVerificationView.class,
                new RouteParameters(IdentityUserEmailVerificationView.ID_PARAM, actionKey)
        );
    }

    @Override
    public URI resetPasswordUri(String actionKey) {
        return route(
                PasswordResetView.class,
                new RouteParameters(PasswordResetView.ID_PARAM, actionKey)
        );
    }

    private URI route(Class<? extends Component> view, RouteParameters parameters) {
        var path = RouteConfiguration.forRegistry(routeRegistryProvider.getRegistry()).getUrl(view, parameters);
        var base = StringUtils.trimTrailingCharacter(publicUrl, '/');
        return UriComponentsBuilder.fromUriString(base)
                .path("/" + path)
                .build()
                .toUri();
    }
}
