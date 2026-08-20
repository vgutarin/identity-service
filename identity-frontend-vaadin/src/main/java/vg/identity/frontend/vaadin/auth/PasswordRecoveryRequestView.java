package vg.identity.frontend.vaadin.auth;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinServletRequest;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import vg.identity.frontend.vaadin.service.LocalizationService;
import vg.identity.frontend.vaadin.ui.LocalePicker;
import vg.identity.service.IdentityActionTokenService;

/**
 * Public "forgot password" request page. The user enters an email address; on submit the service issues a
 * reset link only for an eligible (verified, attached) account, but this view always shows the same neutral
 * confirmation regardless of the outcome, so it never reveals whether the address is registered
 * (FR-003 / SC-002).
 */
@Slf4j
@Route(value = "recover/password", autoLayout = false)
@AnonymousAllowed
public class PasswordRecoveryRequestView extends VerticalLayout implements HasDynamicTitle {

    private final transient IdentityActionTokenService actionTokenService;
    private final LocalizationService localization;
    private final Span message = new Span();
    private VerticalLayout inputs;

    public PasswordRecoveryRequestView(
            IdentityActionTokenService actionTokenService,
            LocalizationService localization
    ) {
        this.actionTokenService = actionTokenService;
        this.localization = localization;

        setSizeFull();
        setJustifyContentMode(JustifyContentMode.CENTER);
        setAlignItems(Alignment.CENTER);

        message.setText(i18n("password.recovery.prompt"));
        add(new LocalePicker(localization), new H1(i18n("password.recovery.title")), message);

        var email = new EmailField(i18n("password.recovery.email.label"));
        email.setWidthFull();

        var submit = new Button(i18n("password.recovery.submit"));
        submit.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        submit.addClickListener(event -> onSubmit(email.getValue()));

        inputs = new VerticalLayout(email, submit);
        inputs.setPadding(false);
        inputs.setWidthFull();
        inputs.setMaxWidth("360px");
        add(inputs);
    }

    private void onSubmit(String email) {
        // Only call the service for a non-blank address; a blank field still yields the same neutral
        // confirmation so nothing about account existence leaks.
        if (StringUtils.hasText(email)) {
            try {
                actionTokenService.requestPasswordReset(email.trim(), clientKey());
            } catch (RuntimeException e) {
                // Never surface a failure that could reveal account state; log without the address.
                log.warn("Password reset request could not be processed", e);
            }
        }
        // Swap only the input area for the confirmation; keep the language selector and title in place.
        if (inputs != null) {
            remove(inputs);
            inputs = null;
        }
        message.setText(i18n("password.recovery.confirmation"));
    }

    /**
     * Client identifier for per-IP rate limiting (FR-007a). Uses the servlet remote address; behind a trusted
     * proxy this should be replaced with the forwarded client IP. Resolved here at the web layer — the logic
     * layer never reads the servlet request.
     */
    private String clientKey() {
        var request = VaadinServletRequest.getCurrent();
        return request == null ? null : request.getRemoteAddr();
    }

    private String i18n(String key) {
        return localization.i18n(key);
    }

    @Override
    public String getPageTitle() {
        return i18n("password.recovery.title");
    }
}
