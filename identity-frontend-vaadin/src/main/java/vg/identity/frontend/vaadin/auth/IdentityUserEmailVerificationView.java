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
import vg.identity.frontend.vaadin.ui.LocalePicker;
import vg.identity.model.IdentityAction;
import vg.identity.service.IdentityActionTokenService;

import java.net.URL;
import java.util.UUID;

@Slf4j
@Route(value = "verify/email", autoLayout = false)
@AnonymousAllowed
public class IdentityUserEmailVerificationView extends VerticalLayout implements BeforeEnterObserver, HasDynamicTitle {
    private final transient IdentityActionTokenService actionTokenService;
    private final LocalizationService localization;
    private final Span result = new Span();
    private Span bindTelegramSuggestion;

    public IdentityUserEmailVerificationView(
            IdentityActionTokenService actionTokenService,
            LocalizationService localization
    ) {
        this.actionTokenService = actionTokenService;
        this.localization = localization;

        setSizeFull();
        setJustifyContentMode(JustifyContentMode.CENTER);
        setAlignItems(Alignment.CENTER);

        add(new LocalePicker(localization), new H1(i18n("email.verification.title")), result);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var id = event.getLocation()
                .getQueryParameters()
                .getParameters()
                .getOrDefault("id", java.util.List.of())
                .stream()
                .findFirst()
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

    private void showPersonalInformationConsent(IdentityAction.ConfirmEmailInfo confirmEmailInfo) {
        result.setText(i18n("email.verification.consent.required"));

        var consent = new Checkbox(i18n("personal.data.processing.consent.checkbox"));
        consent.addValueChangeListener(event -> {
            if (!event.getValue()) {
                return;
            }

            consent.setEnabled(false);
            remove(consent);
            verify(confirmEmailInfo.id());
        });
        add(consent);
    }

    private void verify(UUID verificationId) {
        removeBindTelegramSuggestion();

        var confirmEmailResult = actionTokenService.confirmEmail(verificationId);
        if (confirmEmailResult.success()) {
            result.setText(i18n("email.verification.success"));
            showBindTelegramSuggestion(confirmEmailResult.bindTelegramUrl());
            return;
        }

        result.setText(i18n("email.verification.link.invalidOrExpired"));
    }

    private void showBindTelegramSuggestion(URL bindTelegramUrl) {
        if (bindTelegramUrl == null) {
            return;
        }

        var link = new Anchor(
                bindTelegramUrl.toExternalForm(),
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
