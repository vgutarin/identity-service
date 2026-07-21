package vg.identity.frontend.vaadin.auth;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dependency.JavaScript;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import lombok.extern.slf4j.Slf4j;
import vg.identity.frontend.vaadin.service.LocalizationService;
import vg.identity.frontend.vaadin.service.VaadinAuthenticationService;
import vg.identity.frontend.vaadin.ui.LocalePicker;
import vg.identity.service.TelegramLoginService;

/**
 * Preliminary Telegram authentication view opened as a Telegram Mini App at {@code verify/telegram}.
 * <p>
 * It reads {@code Telegram.WebApp.initData} from the client, delegates the whole flow to
 * {@link TelegramLoginService} and reacts to its outcome: authenticate the user and redirect to the root page,
 * greet an unknown Telegram user, ask for personal-data consent, or show a single neutral error message.
 */
@Slf4j
@JavaScript("https://telegram.org/js/telegram-web-app.js")
@Route(value = "verify/telegram", autoLayout = false)
@AnonymousAllowed
public class TelegramAuthView extends VerticalLayout implements HasDynamicTitle {

    private final transient TelegramLoginService loginService;
    private final transient VaadinAuthenticationService authenticationService;
    private final LocalizationService localization;

    private final Span result = new Span();
    private final Button retry;
    private Checkbox consent;

    private String initData;

    public TelegramAuthView(
            TelegramLoginService loginService,
            VaadinAuthenticationService authenticationService,
            LocalizationService localization
    ) {
        this.loginService = loginService;
        this.authenticationService = authenticationService;
        this.localization = localization;

        addClassName("telegram-auth-view");
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        retry = new Button(i18n("telegram.auth.retry"), click -> requestTelegramInitData());
        retry.addClassName("telegram-auth-retry");

        add(new LocalePicker(localization), new H1(i18n("telegram.auth.title")), result, retry);
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        requestTelegramInitData();
    }

    private void requestTelegramInitData() {
        removeConsent();
        retry.setVisible(true);
        result.setText(i18n("telegram.auth.connecting"));
        UI.getCurrent().getPage().executeJs("""
                const webApp = window.Telegram && window.Telegram.WebApp;
                const initData = webApp ? webApp.initData : '';
                $0.$server.authenticate(initData);
                """, getElement());
    }

    @ClientCallable
    public void authenticate(String initData) {
        this.initData = initData;
        if (initData == null || initData.isBlank()) {
            result.setText(i18n("telegram.auth.initData.missing"));
            return;
        }

        login(false);
    }

    private void login(boolean consentGranted) {
        TelegramLoginService.Result loginResult;
        try {
            loginResult = loginService.login(initData, consentGranted);
        } catch (RuntimeException e) {
            log.error("Telegram login failed", e);
            showFailure();
            return;
        }

        switch (loginResult.outcome()) {
            case AUTHENTICATED -> onAuthenticated(loginResult);
            case GREETING -> onGreeting(loginResult);
            case CONSENT_REQUIRED -> onConsentRequired();
            case FAILED -> showFailure();
        }
    }

    private void onAuthenticated(TelegramLoginService.Result loginResult) {
        removeConsent();
        retry.setVisible(false);

        if (!authenticationService.authenticate(loginResult.user())) {
            showFailure();
            return;
        }

        result.setText(i18n("telegram.auth.authenticating"));
        UI.getCurrent().getPage().setLocation("/");
    }

    private void onGreeting(TelegramLoginService.Result loginResult) {
        removeConsent();
        retry.setVisible(false);
        result.setText(i18n("telegram.auth.greeting") + " " + loginResult.greetingName());
    }

    private void onConsentRequired() {
        retry.setVisible(false);
        result.setText(i18n("telegram.auth.consent.required"));

        if (consent != null) {
            return;
        }

        consent = new Checkbox(i18n("personal.data.processing.consent.checkbox"));
        consent.addValueChangeListener(event -> {
            if (!Boolean.TRUE.equals(event.getValue())) {
                return;
            }
            consent.setEnabled(false);
            login(true);
        });
        add(consent);
    }

    private void showFailure() {
        removeConsent();
        retry.setVisible(true);
        result.setText(i18n("telegram.auth.failed"));
    }

    private void removeConsent() {
        if (consent == null) {
            return;
        }
        remove(consent);
        consent = null;
    }

    private String i18n(String key) {
        return localization.i18n(key);
    }

    @Override
    public String getPageTitle() {
        return i18n("telegram.auth.title");
    }
}
