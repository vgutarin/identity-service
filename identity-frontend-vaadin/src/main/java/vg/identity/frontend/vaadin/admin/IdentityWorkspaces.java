package vg.identity.frontend.vaadin.admin;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import vg.identity.entity.IdentityWorkspaceEntity;
import vg.identity.frontend.vaadin.MainView;
import vg.identity.frontend.vaadin.Role;
import vg.identity.frontend.vaadin.service.LocalizationService;
import vg.identity.service.IdentityWorkspaceService;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

@PageTitle("Workspaces")
@Route(value = "admin/workspaces", layout = MainView.class)
@RolesAllowed(Role.OWNER)
public class IdentityWorkspaces extends VerticalLayout {

    private final transient IdentityWorkspaceService workspaceService;
    private final LocalizationService localization;
    private final Grid<IdentityWorkspaceEntity> grid = new Grid<>(IdentityWorkspaceEntity.class, false);
    private final DateTimeFormatter dateTimeFormatter;

    public IdentityWorkspaces(IdentityWorkspaceService workspaceService, LocalizationService localization) {
        this.workspaceService = workspaceService;
        this.localization = localization;
        this.dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                .withLocale(localization.getCurrentLocale())
                .withZone(ZoneId.systemDefault());

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        configureGrid();

        var create = new Button(localization.i18n("Create"), VaadinIcon.PLUS.create());
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        create.addClickListener(event -> openForm(new IdentityWorkspaceEntity()));

        var toolbar = new HorizontalLayout(create);
        toolbar.setWidthFull();
        toolbar.setJustifyContentMode(FlexComponent.JustifyContentMode.END);

        add(toolbar, grid);
        expand(grid);

        refreshGrid();
    }

    private void configureGrid() {
        grid.setSizeFull();
        grid.setEmptyStateText(localization.i18n("No workspaces found"));

        grid.addColumn(IdentityWorkspaceEntity::getUniqueId)
                .setHeader(localization.i18n("ID"))
                .setSortable(true)
                .setAutoWidth(true)
                .setFlexGrow(0);
        grid.addColumn(IdentityWorkspaceEntity::getName)
                .setHeader(localization.i18n("Name"))
                .setSortable(true)
                .setAutoWidth(true);
        grid.addColumn(workspace -> format(workspace.getCreatedAt()))
                .setHeader(localization.i18n("Created"))
                .setSortable(true)
                .setComparator(IdentityWorkspaceEntity::getCreatedAt)
                .setAutoWidth(true);
        grid.addColumn(workspace -> format(workspace.getUpdatedAt()))
                .setHeader(localization.i18n("Updated"))
                .setSortable(true)
                .setComparator(IdentityWorkspaceEntity::getUpdatedAt)
                .setAutoWidth(true);
        grid.addComponentColumn(this::actions)
                .setHeader(localization.i18n("Actions"))
                .setAutoWidth(true)
                .setFlexGrow(0);
    }

    private HorizontalLayout actions(IdentityWorkspaceEntity workspace) {
        var edit = new Button(localization.i18n("Edit"), VaadinIcon.EDIT.create());
        edit.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        edit.addClickListener(event -> openForm(workspace));

        var delete = new Button(localization.i18n("Delete"), VaadinIcon.TRASH.create());
        delete.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        delete.addClickListener(event -> confirmDelete(workspace));

        var actions = new HorizontalLayout(edit, delete);
        actions.setPadding(false);
        actions.setSpacing(true);
        return actions;
    }

    private void openForm(IdentityWorkspaceEntity workspace) {
        var editing = workspace.getUniqueId() != null;
        var formWorkspace = editing ? copy(workspace) : workspace;

        var dialog = new Dialog();
        dialog.setHeaderTitle(localization.i18n(editing ? "Edit workspace" : "Create workspace"));
        dialog.setDraggable(true);

        var binder = new Binder<>(IdentityWorkspaceEntity.class);
        var name = new TextField(localization.i18n("Name"));
        name.setWidthFull();
        name.setRequiredIndicatorVisible(true);

        binder.forField(name)
                .asRequired(localization.i18n("Name is required"))
                .withValidator(value -> !value.isBlank(), localization.i18n("Name is required"))
                .bind(IdentityWorkspaceEntity::getName, IdentityWorkspaceEntity::setName);
        binder.readBean(formWorkspace);

        var form = new FormLayout(name);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        var save = new Button(localization.i18n("Save"), event -> save(dialog, binder, formWorkspace));
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        var cancel = new Button(localization.i18n("Cancel"), event -> dialog.close());

        var footer = new HorizontalLayout(cancel, save);
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);

        dialog.add(new VerticalLayout(form));
        dialog.getFooter().add(footer);
        dialog.open();
    }

    private void save(Dialog dialog, Binder<IdentityWorkspaceEntity> binder, IdentityWorkspaceEntity workspace) {
        try {
            binder.writeBean(workspace);
            if (workspace.getUniqueId() == null) {
                workspaceService.create(workspace);
            } else {
                workspaceService.update(workspace);
            }
            dialog.close();
            refreshGrid();
            notify(localization.i18n("Workspace saved"), NotificationVariant.LUMO_SUCCESS);
        } catch (ValidationException ignored) {
            notify(localization.i18n("Fix validation errors"), NotificationVariant.LUMO_ERROR);
        } catch (Exception e) {
            notify(localization.i18n(e), NotificationVariant.LUMO_ERROR);
        }
    }

    private void confirmDelete(IdentityWorkspaceEntity workspace) {
        var dialog = new ConfirmDialog();
        dialog.setHeader(localization.i18n("Delete workspace"));
        dialog.setText(localization.i18n("Delete workspace confirmation"));
        dialog.setCancelable(true);
        dialog.setCancelText(localization.i18n("Cancel"));
        dialog.setConfirmText(localization.i18n("Delete"));
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(event -> delete(workspace));
        dialog.open();
    }

    private void delete(IdentityWorkspaceEntity workspace) {
        try {
            workspaceService.delete(workspace.getUniqueId());
            refreshGrid();
            notify(localization.i18n("Workspace deleted"), NotificationVariant.LUMO_SUCCESS);
        } catch (Exception e) {
            notify(localization.i18n(e), NotificationVariant.LUMO_ERROR);
        }
    }

    private void refreshGrid() {
        grid.setItems(workspaceService.findAll());
    }

    private String format(Instant instant) {
        return instant == null ? "" : dateTimeFormatter.format(instant);
    }

    private IdentityWorkspaceEntity copy(IdentityWorkspaceEntity workspace) {
        return IdentityWorkspaceEntity.builder()
                .uniqueId(workspace.getUniqueId())
                .version(workspace.getVersion())
                .createdAt(workspace.getCreatedAt())
                .updatedAt(workspace.getUpdatedAt())
                .name(workspace.getName())
                .build();
    }

    private void notify(String message, NotificationVariant variant) {
        var notification = Notification.show(message, 3000, Notification.Position.TOP_END);
        notification.addThemeVariants(variant);
    }
}
