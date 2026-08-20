package vg.identity.frontend.vaadin.auth;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import vg.identity.frontend.vaadin.service.LocalizationService;
import vg.identity.frontend.vaadin.service.VaadinAuthenticationService;
import vg.identity.frontend.vaadin.ui.LocalePicker;
import vg.identity.frontend.vaadin.ui.Notifications;
import vg.identity.frontend.vaadin.ui.SetPasswordForm;
import vg.identity.service.IdentityActionTokenProcessorService;
import vg.identity.service.IdentityActionTokenService;
import vg.identity.service.IdentityUserService;

/**
 * Public password-reset page. Opened from the emailed link (the action key is the {@code :id} path segment).
 * A valid, unexpired key renders a {@link SetPasswordForm}; submitting a policy-compliant password updates the
 * credential, invalidates the user's other sessions (FR-014), signs the user in, and redirects home (FR-015).
 * A missing, invalid, expired, tampered, or already-used key shows a single "invalid or expired" message
 * (FR-011).
 */
@Slf4j
@Route(value = "reset/password/:id?", autoLayout = false)
@AnonymousAllowed
public class PasswordResetView extends VerticalLayout implements BeforeEnterObserver, HasDynamicTitle {

    /** Path-parameter name; must match the {@code :id} segment in the {@link Route} template above. */
    public static final String ID_PARAM = "id";

    private final transient IdentityActionTokenService actionTokenService;
    private final transient IdentityActionTokenProcessorService actionTokenProcessorService;
    private final transient IdentityUserService userService;
    private final transient VaadinAuthenticationService authenticationService;
    private final transient SessionRegistry sessionRegistry;
    private final LocalizationService localization;
    private final Span result = new Span();
    private SetPasswordForm form;
    private RouterLink requestNewLink;

    public PasswordResetView(
            IdentityActionTokenService actionTokenService,
            IdentityActionTokenProcessorService actionTokenProcessorService,
            IdentityUserService userService,
            VaadinAuthenticationService authenticationService,
            SessionRegistry sessionRegistry,
            LocalizationService localization
    ) {
        this.actionTokenService = actionTokenService;
        this.actionTokenProcessorService = actionTokenProcessorService;
        this.userService = userService;
        this.authenticationService = authenticationService;
        this.sessionRegistry = sessionRegistry;
        this.localization = localization;

        setSizeFull();
        setJustifyContentMode(JustifyContentMode.CENTER);
        setAlignItems(Alignment.CENTER);

        add(new LocalePicker(localization), new H1(i18n("password.reset.title")), result);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var id = event.getRouteParameters().get(ID_PARAM).orElse(null);

        if (id == null || id.isBlank()) {
            result.setText(i18n("password.reset.link.missing"));
            addRequestNewLink();
            return;
        }

        var info = actionTokenService.findResetPasswordActionInfo(id);
        if (info == null) {
            result.setText(i18n("password.reset.link.invalidOrExpired"));
            addRequestNewLink();
            return;
        }

        result.setText(i18n("password.reset.prompt"));
        form = new SetPasswordForm(localization, password -> submit(info.actionKey(), password));
        add(form);
    }

    private void submit(String actionKey, String rawPassword) {
        try {
            var reset = actionTokenProcessorService.resetPassword(actionKey, rawPassword);
            if (!reset.success()) {
                removeForm();
                result.setText(i18n("password.reset.link.invalidOrExpired"));
                addRequestNewLink();
                return;
            }
            removeForm();
            result.setText(i18n("password.reset.success"));
            signInAfterReset(reset.username());
        } catch (RuntimeException e) {
            // e.g. server-side weak-password rejection — the link is still usable, so keep the form.
            log.warn("Password reset submission rejected", e);
            Notifications.error(localization.i18n(e));
            if (form != null) {
                form.resetSubmit();
            }
        }
    }

    /**
     * Invalidates the user's other active sessions (FR-014), signs the user into the current session
     * (FR-015), and redirects home. If the user cannot be loaded or the session cannot be persisted, the
     * success message still stands and the user can sign in manually.
     */
    private void signInAfterReset(String username) {
        UserDetails principal;
        try {
            principal = userService.loadUserByUsername(username);
        } catch (UsernameNotFoundException e) {
            log.warn("Password was reset but the user could not be loaded for automatic sign-in");
            return;
        }

        invalidateOtherSessions(principal);

        if (authenticationService.authenticate(principal)) {
            UI.getCurrent().getPage().setLocation("/");
        }
    }

    private void invalidateOtherSessions(UserDetails principal) {
        // Expire any sessions already registered for this principal so a pre-reset session cannot outlive the
        // reset. The fresh sign-in below then establishes a new session on the current request.
        sessionRegistry.getAllSessions(principal, false)
                .forEach(session -> session.expireNow());
    }

    /**
     * Offers a way out of a dead-end reset link (missing / invalid / expired / already-used) by linking back
     * to the recovery request page so the user can request a fresh link (US3). Added at most once.
     */
    private void addRequestNewLink() {
        if (requestNewLink == null) {
            requestNewLink = new RouterLink(i18n("password.reset.requestNew"), PasswordRecoveryRequestView.class);
            add(requestNewLink);
        }
    }

    private void removeForm() {
        if (form != null) {
            remove(form);
            form = null;
        }
    }

    private String i18n(String key) {
        return localization.i18n(key);
    }

    @Override
    public String getPageTitle() {
        return i18n("password.reset.title");
    }
}
