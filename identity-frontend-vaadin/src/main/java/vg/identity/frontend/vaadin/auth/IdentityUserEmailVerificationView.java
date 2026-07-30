package vg.identity.frontend.vaadin.auth;

import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import lombok.extern.slf4j.Slf4j;
import vg.identity.frontend.vaadin.service.LocalizationService;
import vg.identity.frontend.vaadin.ui.CredentialsForm;
import vg.identity.frontend.vaadin.ui.LocalePicker;
import vg.identity.frontend.vaadin.ui.Notifications;
import vg.identity.model.IdentityAction;
import vg.identity.model.UserProvisioningDetails;
import vg.identity.service.IdentityActionTokenService;
import vg.identity.service.IdentityActionTokenProcessorService;

import java.net.URI;
import java.util.UUID;

@Slf4j
@Route(value = "verify/email/:id?", autoLayout = false)
@AnonymousAllowed
public class IdentityUserEmailVerificationView extends VerticalLayout implements BeforeEnterObserver, HasDynamicTitle {

    /**
     * Name of the action token path parameter. Must match the {@code :id} segment in the {@link Route}
     * template above; reused by the link builder so the read side and the write side stay in sync. The
     * segment is optional ({@code :id?}) so hitting the bare path shows the "link missing" message instead
     * of a 404.
     */
    public static final String ID_PARAM = "id";

    private final transient IdentityActionTokenService actionTokenService;
    private final transient IdentityActionTokenProcessorService actionTokenProcessorService;
    private final LocalizationService localization;
    private final Span result = new Span();
    private Span bindTelegramSuggestion;
    private CredentialsForm provisioningForm;

    public IdentityUserEmailVerificationView(
            IdentityActionTokenService actionTokenService,
            IdentityActionTokenProcessorService actionTokenProcessorService,
            LocalizationService localization
    ) {
        this.actionTokenService = actionTokenService;
        this.actionTokenProcessorService = actionTokenProcessorService;
        this.localization = localization;

        setSizeFull();
        setJustifyContentMode(JustifyContentMode.CENTER);
        setAlignItems(Alignment.CENTER);

        add(new LocalePicker(localization), new H1(i18n("email.verification.title")), result);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var id = event.getRouteParameters()
                .get(ID_PARAM)
                .orElse(null);

        if (id == null || id.isBlank()) {
            result.setText(i18n("email.verification.link.missing"));
            return;
        }

        UUID verificationId;
        try {
            verificationId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            log.error("Error during email verification: ", e);
            result.setText(i18n("email.verification.link.invalidOrExpired"));
            return;
        }

        var confirmEmailInfo = actionTokenService.findConfirmEmailActionInfo(verificationId);
        if (confirmEmailInfo == null) {
            result.setText(i18n("email.verification.link.invalidOrExpired"));
            return;
        }

        if (confirmEmailInfo.personalInformationConsentGiven()) {
            verify(verificationId);
            return;
        }

        showPersonalInformationConsent(confirmEmailInfo);
    }

    /**
     * A pending invitation with no user yet: collect the display name (prefilled with the invited email) and a
     * password to provision the identity. Consent has already been granted in the preceding step, so the form
     * does not repeat it.
     */
    private void showProvisioningForm(IdentityAction.ConfirmEmailInfo info) {
        result.setText(i18n("email.verification.provisioning.prompt"));

        provisioningForm = new CredentialsForm(
                localization,
                credentials -> submitProvisioning(info.id(), credentials)
        );
        provisioningForm.setDisplayName(info.suggestedDisplayName());
        add(provisioningForm);
    }

    private void submitProvisioning(UUID verificationId, CredentialsForm.Credentials credentials) {
        removeBindTelegramSuggestion();

        try {
            var confirmEmailResult = actionTokenProcessorService.confirmEmail(
                    verificationId,
                    // Consent was granted in the preceding consent step before this form was shown.
                    new UserProvisioningDetails(credentials.displayName(), credentials.rawPassword(), true)
            );
            if (!confirmEmailResult.success()) {
                result.setText(i18n("email.verification.link.invalidOrExpired"));
                provisioningForm.resetSubmit();
                return;
            }

            removeProvisioningForm();
            result.setText(i18n("email.verification.success"));
            showBindTelegramSuggestion(confirmEmailResult.bindTelegramUrl());
        } catch (RuntimeException e) {
            log.error("Error provisioning user during email verification: ", e);
            Notifications.error(localization.i18n(e));
            provisioningForm.resetSubmit();
        }
    }

    private void removeProvisioningForm() {
        if (provisioningForm == null) {
            return;
        }

        remove(provisioningForm);
        provisioningForm = null;
    }

    /**
     * Gathers personal-data consent first, exactly like the Telegram flow. Only once consent is given does the
     * next step appear: a credentials form for a pending invitation (no user yet), or a direct confirmation for
     * an already-provisioned but not-yet-consented user.
     */
    private void showPersonalInformationConsent(IdentityAction.ConfirmEmailInfo confirmEmailInfo) {
        result.setText(i18n("email.verification.consent.required"));

        var consent = new Checkbox(i18n("personal.data.processing.consent.checkbox"));
        consent.addValueChangeListener(event -> {
            if (!event.getValue()) {
                return;
            }

            consent.setEnabled(false);
            remove(consent);

            if (confirmEmailInfo.userUniqueId() == null) {
                showProvisioningForm(confirmEmailInfo);
            } else {
                verify(confirmEmailInfo.id());
            }
        });
        add(consent);
    }

    private void verify(UUID verificationId) {
        removeBindTelegramSuggestion();

        var confirmEmailResult = actionTokenProcessorService.confirmEmail(verificationId);
        if (confirmEmailResult.success()) {
            result.setText(i18n("email.verification.success"));
            showBindTelegramSuggestion(confirmEmailResult.bindTelegramUrl());
            return;
        }

        result.setText(i18n("email.verification.link.invalidOrExpired"));
    }

    private void showBindTelegramSuggestion(URI bindTelegramUrl) {
        if (bindTelegramUrl == null) {
            return;
        }

        var link = new Anchor(
                bindTelegramUrl.toString(),
                i18n("email.verification.telegram.bind.link")
        );
        link.setTarget("_blank");

        bindTelegramSuggestion = new Span();
        bindTelegramSuggestion.add(
                new Text(i18n("email.verification.telegram.bind.suggestion")),
                link
        );
        add(bindTelegramSuggestion);
    }

    private void removeBindTelegramSuggestion() {
        if (bindTelegramSuggestion == null) {
            return;
        }

        remove(bindTelegramSuggestion);
        bindTelegramSuggestion = null;
    }

    private String i18n(String key) {
        return localization.i18n(key);
    }

    @Override
    public String getPageTitle() {
        return i18n("email.verification.title");
    }
}
