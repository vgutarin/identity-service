package vg.identity.frontend.vaadin.admin;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import vg.identity.frontend.vaadin.service.LocalizationService;
import vg.identity.frontend.vaadin.ui.Dialogs;
import vg.identity.frontend.vaadin.ui.Notifications;
import vg.identity.model.IdentityUser;
import vg.identity.model.IdentityWorkspace;
import vg.identity.service.EmailService;
import vg.identity.service.IdentityWorkspaceService;

import java.time.Instant;

class IdentityWorkspaceUsersTab extends VerticalLayout {

    private final transient IdentityWorkspaceService workspaceService;
    private final transient EmailService emailService;
    private final LocalizationService localization;
    private final HorizontalLayout actions = new HorizontalLayout();
    private final Grid<IdentityUser> grid = new Grid<>(IdentityUser.class, false);
    private IdentityWorkspace workspace;

    IdentityWorkspaceUsersTab(
            IdentityWorkspaceService workspaceService,
            EmailService emailService,
            LocalizationService localization
    ) {
        this.workspaceService = workspaceService;
        this.emailService = emailService;
        this.localization = localization;

        setSizeFull();
        setPadding(false);
        setSpacing(true);
        setVisible(false);

        configureActions();
        configureGrid();

        add(actions, grid);
        expand(grid);
    }

    void setWorkspace(IdentityWorkspace workspace) {
        this.workspace = workspace;
        refresh();
    }

    void refresh() {
        refreshActions();
        refreshGrid();
    }

    private void configureActions() {
        actions.setWidthFull();
        actions.setPadding(false);
        actions.setSpacing(true);
        actions.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        actions.setVisible(false);
    }

    private void configureGrid() {
        grid.setSizeFull();
        grid.setEmptyStateText(localization.i18n("No users found"));

        grid.addColumn(IdentityUser::getUsername)
                .setHeader(localization.i18n("Email"))
                .setSortable(true)
                .setAutoWidth(true);
        grid.addColumn(user -> format(user.getCreatedAt()))
                .setHeader(localization.i18n("Created"))
                .setSortable(true)
                .setComparator(IdentityUser::getCreatedAt)
                .setAutoWidth(true);
    }

    private void refreshActions() {
        actions.removeAll();
        actions.setVisible(workspace != null);
        if (workspace == null) {
            return;
        }

        var add = new Button(localization.i18n("Add user"), VaadinIcon.PLUS.create());
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        add.addClickListener(event -> openForm());

        actions.add(add);
    }

    private void openForm() {
        var formUser = new UserForm();

        var dialog = new Dialog();
        dialog.setHeaderTitle(localization.i18n("Add user"));
        dialog.setDraggable(true);
        dialog.setWidth("640px");

        var binder = new Binder<>(UserForm.class);
        var email = new EmailField(localization.i18n("Email"));
        email.setWidthFull();
        email.setRequiredIndicatorVisible(true);
        email.setPlaceholder("user@example.com");
        email.setHelperText(localization.i18n("Username must be a valid email address"));
        email.setErrorMessage(localization.i18n("Enter a valid email address"));

        binder.forField(email)
                .asRequired(localization.i18n("Email is required"))
                .withValidator(emailService::validateEmail, localization.i18n("Enter a valid email address"))
                .bind(UserForm::email, UserForm::email);
        binder.readBean(formUser);

        var form = new FormLayout(email);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        var save = new Button(localization.i18n("Save"), event -> save(dialog, binder, formUser));
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        var cancel = new Button(localization.i18n("Cancel"), event -> dialog.close());

        dialog.add(new VerticalLayout(form));
        dialog.getFooter().add(Dialogs.footer(cancel, save));
        dialog.open();
    }

    private void save(Dialog dialog, Binder<UserForm> binder, UserForm user) {
        try {
            binder.writeBean(user);
            workspace = workspaceService.addUser(workspace.getUniqueId(), user.email());
            dialog.close();
            refreshGrid();
            Notifications.success(localization.i18n("User saved"));
        } catch (ValidationException ignored) {
            Notifications.error(localization.i18n("Fix validation errors"));
        } catch (Exception e) {
            Notifications.error(localization.i18n(e));
        }
    }

    private void refreshGrid() {
        if (workspace == null) {
            grid.setItems();
            return;
        }

        grid.setItems(workspaceService.getUsers(workspace.getUniqueId()));
    }

    private String format(Instant instant) {
        return localization.formatDateTime(instant);
    }

    private static class UserForm {
        private String email;

        private String email() {
            return email;
        }

        private void email(String email) {
            this.email = email;
        }
    }
}
