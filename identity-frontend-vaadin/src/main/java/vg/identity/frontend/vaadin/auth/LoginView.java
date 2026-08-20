package vg.identity.frontend.vaadin.auth;

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import vg.identity.frontend.vaadin.service.LocalizationService;
import vg.identity.frontend.vaadin.ui.LocalePicker;

@Route("login")
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver, HasDynamicTitle {

    private final transient LocalizationService localization;
    private final LoginForm login = new LoginForm();

    public LoginView(LocalizationService localization) {
        this.localization = localization;
        addClassName("login-view");
        setSizeFull();

        setJustifyContentMode(JustifyContentMode.CENTER);
        setAlignItems(Alignment.CENTER);

        login.setAction("login");
        login.setI18n(loginI18n());
        // LoginForm renders a built-in "Forgot password" button; route it to the recovery request view
        // rather than adding a second, separate link.
        login.addForgotPasswordListener(event ->
                login.getUI().ifPresent(ui -> ui.navigate(PasswordRecoveryRequestView.class)));

        add(new LocalePicker(localization), new H1(i18n("project.name")), login);
    }

    /** Fully localized labels for the login form and its error message. */
    private LoginI18n loginI18n() {
        var config = LoginI18n.createDefault();

        var form = config.getForm();
        form.setTitle(i18n("login.form.title"));
        form.setUsername(i18n("login.form.username"));
        form.setPassword(i18n("login.form.password"));
        form.setSubmit(i18n("login.form.submit"));
        form.setForgotPassword(i18n("login.forgotPassword.link"));
        config.setForm(form);

        var error = config.getErrorMessage();
        error.setTitle(i18n("login.error.title"));
        error.setMessage(i18n("login.error.message"));
        config.setErrorMessage(error);

        return config;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent beforeEnterEvent) {
        if (beforeEnterEvent.getLocation().getQueryParameters().getParameters().containsKey("error")) {
            login.setError(true);
        }
    }

    @Override
    public String getPageTitle() {
        return i18n("login.form.title");
    }

    private String i18n(String key) {
        return localization.i18n(key);
    }
}
