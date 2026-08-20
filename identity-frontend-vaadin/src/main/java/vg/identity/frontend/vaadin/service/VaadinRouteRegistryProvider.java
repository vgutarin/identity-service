package vg.identity.frontend.vaadin.service;

import com.vaadin.flow.server.RouteRegistry;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import org.springframework.stereotype.Component;

/**
 * Captures the application-scoped {@link RouteRegistry} once, at Vaadin service initialization, and holds it
 * for later, request-independent use.
 * <p>
 * Vaadin invokes {@link #serviceInit} once at startup (Spring auto-detects this bean as a
 * {@link VaadinServiceInitListener}). The registry obtained here is the same one Vaadin populates with the
 * {@code @Route} views, so links can be resolved from any thread — including off a Vaadin request thread,
 * as when a workspace invitation or a queued recovery email builds its link — without touching
 * {@code VaadinService.getCurrent()} or the servlet context.
 */
@Component
public class VaadinRouteRegistryProvider implements VaadinServiceInitListener {

    private volatile RouteRegistry routeRegistry;

    @Override
    public void serviceInit(ServiceInitEvent event) {
        this.routeRegistry = event.getSource().getRouter().getRegistry();
    }

    /**
     * @return the application route registry captured at service init.
     * @throws IllegalStateException if called before the Vaadin service has initialized (should not happen
     *                               at runtime, when links are built well after startup).
     */
    public RouteRegistry getRegistry() {
        var registry = routeRegistry;
        if (registry == null) {
            throw new IllegalStateException("Vaadin routes are not initialized yet");
        }
        return registry;
    }
}
