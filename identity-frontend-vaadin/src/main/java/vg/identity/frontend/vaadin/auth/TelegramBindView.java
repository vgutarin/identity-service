package vg.identity.frontend.vaadin.auth;

import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.JavaScript;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import vg.identity.frontend.vaadin.service.LocalizationService;
import vg.identity.frontend.vaadin.ui.LocalePicker;
import vg.identity.service.TelegramUserBindingService;

@JavaScript("https://telegram.org/js/telegram-web-app.js")
@Route(value = "verify/telegram/bind", autoLayout = false)
@AnonymousAllowed
public class TelegramBindView extends VerticalLayout implements HasDynamicTitle {

    private final TelegramUserBindingService bindingService;
    private final LocalizationService localization;
    private final Span result = new Span();
    private final Button retry;

    public TelegramBindView(
            TelegramUserBindingService bindingService,
            LocalizationService localization
    ) {
        this.bindingService = bindingService;
        this.localization = localization;

        addClassName("telegram-auth-view");
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        retry = new Button(i18n("telegram.verification.retry"), click -> requestTelegramInitData());
        retry.addClassName("telegram-auth-retry");

        add(new LocalePicker(localization), new H1(i18n("telegram.verification.title")), result, retry);
    }

    @Override
    protected void onAttach(com.vaadin.flow.component.AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        requestTelegramInitData();
    }

    private void requestTelegramInitData() {
        result.setText(i18n("telegram.verification.connecting"));
        UI.getCurrent().getPage().executeJs("""
                const webApp = window.Telegram && window.Telegram.WebApp;
                const initData = webApp ? webApp.initData : '';
                $0.$server.authenticate(initData);
                """, getElement());
    }

    @ClientCallable
    public void authenticate(String initData) {
        if (initData == null || initData.isBlank()) {
            result.setText(i18n("telegram.verification.initData.missing"));
            return;
        }

        var result = bindingService.bind(initData);
        if (result.success()) {
            this.result.setText(
                    i18n("telegram.verification.success") + " %s !".formatted(result.telegramUser().displayName())
            );
            retry.setVisible(false);
            return;
        }

        this.result.setText(i18n("telegram.verification.failed"));
    }

    private String i18n(String key) {
        return localization.i18n(key);
    }

    @Override
    public String getPageTitle() {
        return i18n("telegram.verification.title");
    }
}
