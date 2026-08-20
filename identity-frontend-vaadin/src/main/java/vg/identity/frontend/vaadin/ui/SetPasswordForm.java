package vg.identity.frontend.vaadin.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.value.ValueChangeMode;
import vg.identity.frontend.vaadin.service.LocalizationService;
import vg.identity.model.PasswordPolicy;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Password-only form for the password-recovery flow: a new password entered twice (both with the reveal
 * toggle), no display name — the account already exists, so recovery only needs the new password. This is the
 * sibling of {@link CredentialsForm} (which additionally collects a display name for provisioning).
 * <p>
 * The rule is validated inline with {@link PasswordPolicy#isStrong(String)}, the same predicate the backend
 * enforces, reusing the shared {@code credentials.password.*} message keys. On a valid submit it invokes
 * {@code onSubmit} with the raw password.
 */
public class SetPasswordForm extends VerticalLayout {

    private final transient LocalizationService localization;
    private final Binder<FormModel> binder = new Binder<>(FormModel.class);
    private final Button submit;

    public SetPasswordForm(LocalizationService localization, Consumer<String> onSubmit) {
        this.localization = localization;
        setPadding(false);
        setSpacing(false);
        setWidthFull();
        setMaxWidth("360px");

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

        add(Dialogs.singleColumnForm(password, confirm));

        submit = new Button(i18n("password.reset.submit"), event -> onSubmit(onSubmit));
        submit.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        add(submit);

        binder.readBean(new FormModel());
    }

    /**
     * Re-enables the submit button after a backend rejection so the user can correct the input and retry.
     */
    public void resetSubmit() {
        submit.setEnabled(true);
    }

    private void onSubmit(Consumer<String> onSubmit) {
        var model = new FormModel();
        try {
            binder.writeBean(model);
        } catch (ValidationException e) {
            // Field-level messages are already shown inline by the Binder.
            return;
        }
        submit.setEnabled(false);
        onSubmit.accept(model.getPassword());
    }

    private String i18n(String key) {
        return localization.i18n(key);
    }

    /**
     * Mutable backing bean for the {@link Binder}.
     */
    static class FormModel {
        private String password;
        private String confirm;

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
