package vg.identity.frontend.vaadin.admin;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import vg.identity.frontend.vaadin.service.LocalizationService;
import vg.identity.frontend.vaadin.ui.Dialogs;
import vg.identity.frontend.vaadin.ui.Notifications;
import vg.identity.model.IdentityApplication;
import vg.identity.model.IdentityApiKey;
import vg.identity.model.IdentityWorkspace;
import vg.identity.model.application.TelegramBot;
import vg.identity.service.IdentityApiKeyService;
import vg.identity.service.IdentityApplicationService;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.function.Consumer;

class IdentityWorkspaceApplicationsTab extends VerticalLayout {

    private final transient IdentityApplicationService applicationService;
    private final transient IdentityApiKeyService apiKeyService;
    private final LocalizationService localization;
    private final HorizontalLayout actions = new HorizontalLayout();
    private final Grid<IdentityApplication> grid = new Grid<>(IdentityApplication.class, false);
    private IdentityWorkspace workspace;

    IdentityWorkspaceApplicationsTab(
            IdentityApplicationService applicationService,
            IdentityApiKeyService apiKeyService,
            LocalizationService localization
    ) {
        this.applicationService = applicationService;
        this.apiKeyService = apiKeyService;
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
        var apiKeys = new Button(localization.i18n("API keys"), VaadinIcon.KEY.create());
        apiKeys.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        apiKeys.addClickListener(event -> openApiKeysDialog(application));

        var edit = new Button(localization.i18n("Edit"), VaadinIcon.EDIT.create());
        edit.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        edit.addClickListener(event -> openEditForm(application));

        var delete = new Button(localization.i18n("Delete"), VaadinIcon.TRASH.create());
        delete.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        delete.addClickListener(event -> confirmDelete(application));

        return actionLayout(apiKeys, edit, delete);
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
        var dialog = Dialogs.form(title);

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

        var form = Dialogs.singleColumnForm(name, botToken);

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

    private void openApiKeysDialog(IdentityApplication application) {
        var dialog = Dialogs.form(localization.i18n("API keys"));
        var keyGrid = new Grid<IdentityApiKey>(IdentityApiKey.class, false);
        keyGrid.setWidthFull();
        keyGrid.setEmptyStateText(localization.i18n("No API keys found"));
        keyGrid.addColumn(IdentityApiKey::label)
                .setHeader(localization.i18n("Label"))
                .setAutoWidth(true);
        keyGrid.addColumn(key -> format(key.expiresAt()))
                .setHeader(localization.i18n("Expires"))
                .setAutoWidth(true);
        keyGrid.addColumn(key -> key.revokedAt() == null ? localization.i18n("Active") : localization.i18n("Revoked"))
                .setHeader(localization.i18n("Status"))
                .setAutoWidth(true);

        var refresh = new Runnable() {
            @Override
            public void run() {
                keyGrid.setItems(apiKeyService.findForApplication(application.getUniqueId()));
            }
        };
        keyGrid.addComponentColumn(key -> revokeKeyButton(application, key, refresh))
                .setHeader(localization.i18n("Actions"))
                .setAutoWidth(true)
                .setFlexGrow(0);

        var generate = new Button(localization.i18n("Generate API key"), VaadinIcon.PLUS.create());
        generate.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        generate.addClickListener(event -> openIssueApiKeyDialog(application, refresh));

        var close = new Button(localization.i18n("Close"), event -> dialog.close());
        dialog.add(new VerticalLayout(keyGrid));
        dialog.getFooter().add(Dialogs.footer(close, generate));
        refresh.run();
        dialog.open();
    }

    private Button revokeKeyButton(IdentityApplication application, IdentityApiKey key, Runnable refresh) {
        var revoke = new Button(localization.i18n("Revoke"), VaadinIcon.BAN.create());
        revoke.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        revoke.setEnabled(key.revokedAt() == null);
        revoke.addClickListener(event -> confirmRevokeKey(application, key, refresh));
        return revoke;
    }

    private void confirmRevokeKey(IdentityApplication application, IdentityApiKey key, Runnable refresh) {
        Dialogs.confirmAction(
                localization,
                "Revoke API key",
                "Revoke API key confirmation",
                "Revoke",
                () -> revokeKey(application, key, refresh)
        );
    }

    private void revokeKey(IdentityApplication application, IdentityApiKey key, Runnable refresh) {
        try {
            apiKeyService.revokeForApplication(application.getUniqueId(), key.id());
            refresh.run();
            Notifications.success(localization.i18n("API key revoked"));
        } catch (Exception e) {
            Notifications.error(localization.i18n(e));
        }
    }

    private void openIssueApiKeyDialog(IdentityApplication application, Runnable refresh) {
        var dialog = Dialogs.form(localization.i18n("Generate API key"));
        var formApiKey = new ApiKeyForm();
        formApiKey.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(90));

        var binder = new Binder<>(ApiKeyForm.class);
        var label = new TextField(localization.i18n("Label"));
        label.setWidthFull();
        label.setRequiredIndicatorVisible(true);

        var expiresAt = new DateTimePicker(localization.i18n("Expires at (UTC)"));
        expiresAt.setWidthFull();
        expiresAt.setRequiredIndicatorVisible(true);

        binder.forField(label)
                .asRequired(localization.i18n("Label is required"))
                .withValidator(value -> !value.isBlank() && value.strip().length() <= IdentityApiKeyService.MAX_LABEL_LENGTH,
                        localization.i18n("Label must be 256 characters or fewer"))
                .bind(ApiKeyForm::getLabel, ApiKeyForm::setLabel);
        binder.forField(expiresAt)
                .asRequired(localization.i18n("Expiry is required"))
                .withValidator(value -> value != null && value.toInstant(ZoneOffset.UTC).isAfter(Instant.now()),
                        localization.i18n("Expiry must be in the future"))
                .bind(ApiKeyForm::getExpiresAt, ApiKeyForm::setExpiresAt);
        binder.readBean(formApiKey);

        var generate = new Button(localization.i18n("Generate"), event -> {
            try {
                binder.writeBean(formApiKey);
                var issued = apiKeyService.issueForApplication(
                        application.getUniqueId(),
                        formApiKey.getLabel(),
                        formApiKey.getExpiresAt().toInstant(ZoneOffset.UTC)
                );
                dialog.close();
                refresh.run();
                showIssuedApiKey(issued.value());
            } catch (ValidationException ignored) {
                Notifications.error(localization.i18n("Fix validation errors"));
            } catch (Exception e) {
                Notifications.error(localization.i18n(e));
            }
        });
        generate.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        var cancel = new Button(localization.i18n("Cancel"), event -> dialog.close());

        dialog.add(new VerticalLayout(Dialogs.singleColumnForm(label, expiresAt)));
        dialog.getFooter().add(Dialogs.footer(cancel, generate));
        dialog.open();
    }

    private void showIssuedApiKey(String value) {
        var dialog = Dialogs.form(localization.i18n("Copy API key now"));
        var apiKey = new TextArea(localization.i18n("API key"));
        apiKey.setValue(value);
        apiKey.setReadOnly(true);
        apiKey.setWidthFull();
        apiKey.setHelperText(localization.i18n("This key will not be shown again"));

        var copy = new Button(localization.i18n("Copy"), event ->
                dialog.getUI().ifPresent(ui -> ui.getPage().executeJs("navigator.clipboard.writeText($0)", value))
        );
        copy.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        var close = new Button(localization.i18n("Close"), event -> dialog.close());

        dialog.add(new VerticalLayout(apiKey));
        dialog.getFooter().add(Dialogs.footer(close, copy));
        dialog.open();
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

    private static class ApiKeyForm {
        private String label;
        private LocalDateTime expiresAt;

        private String getLabel() {
            return label;
        }

        private void setLabel(String label) {
            this.label = label;
        }

        private LocalDateTime getExpiresAt() {
            return expiresAt;
        }

        private void setExpiresAt(LocalDateTime expiresAt) {
            this.expiresAt = expiresAt;
        }
    }
}
