package vg.identity.frontend.vaadin.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.value.ValueChangeMode;
import vg.identity.frontend.vaadin.service.LocalizationService;
import vg.identity.model.PasswordPolicy;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Shared credentials form used by both provisioning flows (email verification and Telegram sign-in) when a
 * brand-new identity user is created. Collects a display name and a password (entered twice, both with the
 * reveal toggle). Consent is gathered by each flow <em>before</em> this form is shown, so the form itself does
 * not deal with consent. The password rule is validated inline with {@link PasswordPolicy#isStrong(String)},
 * the same predicate the backend enforces, so the two cannot drift.
 * <p>
 * Instantiated with {@code new} (like {@link LocalePicker}); on a valid submit it invokes {@code onSubmit} with
 * the collected {@link Credentials}.
 */
public class CredentialsForm extends VerticalLayout {

    public record Credentials(String displayName, String rawPassword) {
    }

    private final transient LocalizationService localization;
    private final Binder<FormModel> binder = new Binder<>(FormModel.class);
    private final Button submit;

    private final TextField displayName;

    public CredentialsForm(LocalizationService localization, Consumer<Credentials> onSubmit) {
        this.localization = localization;
        setPadding(false);
        setSpacing(false);
        // Full width on narrow screens, but capped on desktop so the form doesn't stretch across the page.
        setWidthFull();
        setMaxWidth("360px");

        displayName = new TextField(i18n("credentials.displayName.label"));
        displayName.setWidthFull();
        displayName.setRequiredIndicatorVisible(true);

        var password = new PasswordField(i18n("credentials.password.label"));
        password.setWidthFull();
        password.setRequiredIndicatorVisible(true);
        password.setRevealButtonVisible(true);
        password.setValueChangeMode(ValueChangeMode.EAGER);
        password.setHelperText(i18n("credentials.password.helper"));

        var confirm = new PasswordField(i18n("credentials.password.confirm.label"));
        confirm.setWidthFull();
        confirm.setRequiredIndicatorVisible(true);
        confirm.setRevealButtonVisible(true);
        confirm.setValueChangeMode(ValueChangeMode.EAGER);

        binder.forField(displayName)
                .asRequired(i18n("credentials.displayName.required"))
                .withValidator(value -> !value.isBlank(), i18n("credentials.displayName.required"))
                .bind(FormModel::getDisplayName, FormModel::setDisplayName);
        binder.forField(password)
                .asRequired(i18n("credentials.password.required"))
                .withValidator(PasswordPolicy::isStrong, i18n("credentials.password.weak"))
                .bind(FormModel::getPassword, FormModel::setPassword);
        binder.forField(confirm)
                .asRequired(i18n("credentials.password.required"))
                .withValidator(value -> Objects.equals(value, password.getValue()), i18n("credentials.password.mismatch"))
                .bind(FormModel::getConfirm, FormModel::setConfirm);
        // Re-check the confirm field when the primary password changes so the mismatch message stays in sync.
        password.addValueChangeListener(event -> binder.validate());

        add(Dialogs.singleColumnForm(displayName, password, confirm));

        submit = new Button(i18n("credentials.submit"), event -> onSubmit(onSubmit));
        submit.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        add(submit);

        binder.readBean(new FormModel());
    }

    /**
     * Prefills the display name (Telegram profile name, or the invited email address).
     */
    public void setDisplayName(String value) {
        displayName.setValue(value == null ? "" : value);
    }

    /**
     * Re-enables the submit button after a backend rejection so the user can correct the input and retry.
     */
    public void resetSubmit() {
        submit.setEnabled(true);
    }

    private void onSubmit(Consumer<Credentials> onSubmit) {
        var model = new FormModel();
        try {
            binder.writeBean(model);
        } catch (ValidationException e) {
            // Field-level messages are already shown inline by the Binder.
            return;
        }
        submit.setEnabled(false);
        onSubmit.accept(new Credentials(model.getDisplayName().trim(), model.getPassword()));
    }

    private String i18n(String key) {
        return localization.i18n(key);
    }

    /**
     * Mutable backing bean for the {@link Binder}.
     */
    static class FormModel {
        private String displayName;
        private String password;
        private String confirm;

        String getDisplayName() {
            return displayName;
        }

        void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        String getPassword() {
            return password;
        }

        void setPassword(String password) {
            this.password = password;
        }

        String getConfirm() {
            return confirm;
        }

        void setConfirm(String confirm) {
            this.confirm = confirm;
        }
    }
}
