package vg.identity.frontend.vaadin.service;

import com.vaadin.flow.router.RouteConfiguration;
import com.vaadin.flow.server.RouteRegistry;
import com.vaadin.flow.server.VaadinServletContext;
import com.vaadin.flow.server.startup.ApplicationRouteRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockServletContext;
import vg.identity.frontend.vaadin.auth.IdentityUserEmailVerificationView;
import vg.identity.frontend.vaadin.auth.PasswordResetView;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The frontend link builder resolves routes through Vaadin's {@code RouteConfiguration.forRegistry(...)}
 * using a {@link RouteRegistry} captured at startup — no {@code VaadinService.getCurrent()} and no request
 * thread. This is a plain unit test: it fabricates a populated registry and feeds it via the provider, then
 * builds links on the JUnit thread (off any Vaadin request), the condition under which invitations and
 * queued recovery emails run.
 */
class IdentityActionLinkBuilderVaadinTest {

    private IdentityActionLinkBuilderVaadin linkBuilder;

    @BeforeEach
    void setUp() {
        // A registry populated with the two action views (stands in for Vaadin's startup route registration).
        RouteRegistry registry =
                ApplicationRouteRegistry.getInstance(new VaadinServletContext(new MockServletContext()));
        var routes = RouteConfiguration.forRegistry(registry);
        routes.setAnnotatedRoute(IdentityUserEmailVerificationView.class);
        routes.setAnnotatedRoute(PasswordResetView.class);

        var registryProvider = new VaadinRouteRegistryProvider() {
            @Override
            public RouteRegistry getRegistry() {
                return registry;
            }
        };
        linkBuilder = new IdentityActionLinkBuilderVaadin("https://test.example.com", registryProvider);
    }

    @Test
    void resetPasswordUri_buildsAbsoluteUrlFromRouteRegistry() {
        assertThat(linkBuilder.resetPasswordUri("42_secret").toString())
                .isEqualTo("https://test.example.com/reset/password/42_secret");
    }

    @Test
    void confirmationEmailUri_buildsAbsoluteUrlFromRouteRegistry() {
        assertThat(linkBuilder.confirmationEmailUri("7_secret").toString())
                .isEqualTo("https://test.example.com/verify/email/7_secret");
    }
}
