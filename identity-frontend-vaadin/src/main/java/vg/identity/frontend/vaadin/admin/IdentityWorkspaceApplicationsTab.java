package vg.identity.frontend.vaadin.admin;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import vg.identity.frontend.vaadin.service.LocalizationService;
import vg.identity.frontend.vaadin.ui.Dialogs;
import vg.identity.frontend.vaadin.ui.Notifications;
import vg.identity.model.IdentityApplication;
import vg.identity.model.IdentityWorkspace;
import vg.identity.model.application.TelegramBot;
import vg.identity.service.IdentityApplicationService;

import java.time.Instant;
import java.util.function.Consumer;

class IdentityWorkspaceApplicationsTab extends VerticalLayout {

    private final transient IdentityApplicationService applicationService;
    private final LocalizationService localization;
    private final HorizontalLayout actions = new HorizontalLayout();
    private final Grid<IdentityApplication> grid = new Grid<>(IdentityApplication.class, false);
    private IdentityWorkspace workspace;

    IdentityWorkspaceApplicationsTab(
            IdentityApplicationService applicationService,
            LocalizationService localization
    ) {
        this.applicationService = applicationService;
        this.localization = localization;

        setSizeFull();
        setPadding(false);
        setSpacing(true);

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
        grid.setEmptyStateText(localization.i18n("No applications found"));

        grid.addColumn(IdentityApplication::getName)
                .setHeader(localization.i18n("Name"))
                .setSortable(true)
                .setAutoWidth(true);
        grid.addColumn(IdentityApplication::getUri)
                .setHeader(localization.i18n("URI"))
                .setSortable(true)
                .setAutoWidth(true);
        grid.addColumn(application -> format(application.getCreatedAt()))
                .setHeader(localization.i18n("Created"))
                .setSortable(true)
                .setComparator(IdentityApplication::getCreatedAt)
                .setAutoWidth(true);
        grid.addColumn(application -> format(application.getUpdatedAt()))
                .setHeader(localization.i18n("Updated"))
                .setSortable(true)
                .setComparator(IdentityApplication::getUpdatedAt)
                .setAutoWidth(true);
        grid.addComponentColumn(this::rowActions)
                .setHeader(localization.i18n("Actions"))
                .setAutoWidth(true)
                .setFlexGrow(0);
    }

    private void refreshActions() {
        actions.removeAll();
        actions.setVisible(workspace != null);
        if (workspace == null) {
            return;
        }

        var add = new Button(localization.i18n("Add Telegram bot"), VaadinIcon.PLUS.create());
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        add.addClickListener(event -> openTelegramBotForm());

        actions.add(add);
    }

    private HorizontalLayout rowActions(IdentityApplication application) {
        var edit = new Button(localization.i18n("Edit"), VaadinIcon.EDIT.create());
        edit.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        edit.addClickListener(event -> openEditForm(application));

        var delete = new Button(localization.i18n("Delete"), VaadinIcon.TRASH.create());
        delete.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        delete.addClickListener(event -> confirmDelete(application));

        return actionLayout(edit, delete);
    }

    private HorizontalLayout actionLayout(Component... actions) {
        var layout = new HorizontalLayout(actions);
        layout.setPadding(false);
        layout.setSpacing(true);
        return layout;
    }

    private void openTelegramBotForm() {
        openBotDialog(localization.i18n("Add Telegram bot"), new TelegramBotForm(), formBot ->
                applicationService.createTelegramBotApplication(
                        workspace.getUniqueId(),
                        formBot.getName(),
                        TelegramBot.builder()
                                .token(formBot.getBotToken())
                                .build()
                ));
    }

    private void openEditForm(IdentityApplication application) {
        var formBot = new TelegramBotForm();
        formBot.setName(application.getName());

        openBotDialog(localization.i18n("Edit Telegram bot"), formBot, form ->
                applicationService.updateTelegramBotApplication(
                        application.getUniqueId(),
                        application.getVersion(),
                        form.getName(),
                        TelegramBot.builder()
                                .token(form.getBotToken())
                                .build()
                ));
    }

    private void openBotDialog(String title, TelegramBotForm formBot, Consumer<TelegramBotForm> onSave) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(title);
        dialog.setDraggable(true);
        dialog.setWidth("640px");

        var binder = new Binder<>(TelegramBotForm.class);
        var name = new TextField(localization.i18n("Name"));
        name.setWidthFull();
        name.setRequiredIndicatorVisible(true);

        var botToken = new PasswordField(localization.i18n("Bot token"));
        botToken.setWidthFull();
        botToken.setRequiredIndicatorVisible(true);
        botToken.setHelperText(localization.i18n("Telegram bot token helper"));

        binder.forField(name)
                .asRequired(localization.i18n("Name is required"))
                .withValidator(value -> !value.isBlank(), localization.i18n("Name is required"))
                .bind(TelegramBotForm::getName, TelegramBotForm::setName);
        binder.forField(botToken)
                .asRequired(localization.i18n("Bot token is required"))
                .withValidator(value -> !value.isBlank(), localization.i18n("Bot token is required"))
                .bind(TelegramBotForm::getBotToken, TelegramBotForm::setBotToken);
        binder.readBean(formBot);

        var form = new FormLayout(name, botToken);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        var save = new Button(localization.i18n("Save"), event -> saveBot(dialog, binder, formBot, onSave));
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        var cancel = new Button(localization.i18n("Cancel"), event -> dialog.close());

        dialog.add(new VerticalLayout(form));
        dialog.getFooter().add(Dialogs.footer(cancel, save));
        dialog.open();
    }

    private void saveBot(Dialog dialog, Binder<TelegramBotForm> binder, TelegramBotForm formBot, Consumer<TelegramBotForm> onSave) {
        try {
            binder.writeBean(formBot);
            onSave.accept(formBot);
            dialog.close();
            refreshGrid();
            Notifications.success(localization.i18n("Application saved"));
        } catch (ValidationException ignored) {
            Notifications.error(localization.i18n("Fix validation errors"));
        } catch (Exception e) {
            Notifications.error(localization.i18n(e));
        }
    }

    private void confirmDelete(IdentityApplication application) {
        Dialogs.confirmDelete(
                localization,
                "Delete application",
                "Delete application confirmation",
                () -> delete(application)
        );
    }

    private void delete(IdentityApplication application) {
        try {
            applicationService.delete(application.getUniqueId());
            refreshGrid();
            Notifications.success(localization.i18n("Application deleted"));
        } catch (Exception e) {
            Notifications.error(localization.i18n(e));
        }
    }

    private void refreshGrid() {
        if (workspace == null) {
            grid.setItems();
            return;
        }

        var applications = applicationService.findByWorkspaceUniqueId(workspace.getUniqueId());

        grid.setItems(applications);
    }

    private String format(Instant instant) {
        return localization.formatDateTime(instant);
    }

    private static class TelegramBotForm {
        private String name;
        private String botToken;

        private String getName() {
            return name;
        }

        private void setName(String name) {
            this.name = name;
        }

        private String getBotToken() {
            return botToken;
        }

        private void setBotToken(String botToken) {
            this.botToken = botToken;
        }
    }
}
