package vg.identity.frontend.vaadin.admin;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
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
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import vg.identity.frontend.vaadin.MainView;
import vg.identity.frontend.vaadin.Role;
import vg.identity.frontend.vaadin.service.LocalizationService;
import vg.identity.model.IdentityApplication;
import vg.identity.model.IdentityWorkspace;
import vg.identity.service.IdentityApplicationService;
import vg.identity.service.IdentityWorkspaceService;
import vg.unique.id.model.UniqueId;

import java.time.Instant;

@PageTitle("Applications")
@Route(value = "admin/workspaces/:workspaceId/applications", layout = MainView.class)
@RolesAllowed(Role.OWNER)
public class IdentityWorkspaceApplications extends VerticalLayout implements BeforeEnterObserver {

    private static final String WORKSPACE_ID_PARAMETER = "workspaceId";

    private final transient IdentityWorkspaceService workspaceService;
    private final transient IdentityApplicationService applicationService;
    private final LocalizationService localization;
    private final HorizontalLayout toolbar = new HorizontalLayout();
    private final Grid<IdentityApplication> grid = new Grid<>(IdentityApplication.class, false);
    private IdentityWorkspace workspace;

    public IdentityWorkspaceApplications(
            IdentityWorkspaceService workspaceService,
            IdentityApplicationService applicationService,
            LocalizationService localization
    ) {
        this.workspaceService = workspaceService;
        this.applicationService = applicationService;
        this.localization = localization;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        configureToolbar();
        configureGrid();

        add(toolbar, grid);
        expand(grid);
    }

    private void configureToolbar() {
        toolbar.setWidthFull();
        toolbar.setAlignItems(FlexComponent.Alignment.CENTER);
        toolbar.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        toolbar.setVisible(false);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        workspace = event.getRouteParameters()
                .get(WORKSPACE_ID_PARAMETER)
                .map(this::loadWorkspace)
                .orElseThrow();
        refreshToolbar();
        refreshGrid();
    }

    private void refreshToolbar() {
        toolbar.removeAll();
        toolbar.setVisible(workspace != null);
        if (workspace == null) {
            return;
        }

        var workspaceName = new Button(workspace.getName(), VaadinIcon.ARROW_LEFT.create());
        workspaceName.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        workspaceName.addClassName("workspace-context-button");
        workspaceName.addClickListener(event -> UI.getCurrent().navigate(IdentityWorkspaces.class));

        var add = new Button(localization.i18n("Add application"), VaadinIcon.PLUS.create());
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        add.addClickListener(event -> openForm(workspace, new IdentityApplication()));

        toolbar.add(workspaceName, add);
        toolbar.expand(workspaceName);
    }

    private void configureGrid() {
        grid.setSizeFull();
        grid.setEmptyStateText(localization.i18n("No applications found"));

        grid.addColumn(application -> application.getUniqueId() == null ? "" : application.getUniqueId())
                .setHeader(localization.i18n("ID"))
                .setSortable(true)
                .setAutoWidth(true)
                .setFlexGrow(0);
        grid.addColumn(IdentityApplication::getName)
                .setHeader(localization.i18n("Name"))
                .setSortable(true)
                .setAutoWidth(true);
        grid.addColumn(IdentityApplication::getData)
                .setHeader(localization.i18n("Data"))
                .setSortable(true)
                .setAutoWidth(true);
        grid.addColumn(application -> format(application.getCreatedAt()))
                .setHeader(localization.i18n("Created"))
                .setSortable(true)
                .setAutoWidth(true);
        grid.addColumn(application -> format(application.getUpdatedAt()))
                .setHeader(localization.i18n("Updated"))
                .setSortable(true)
                .setAutoWidth(true);
        grid.addComponentColumn(this::actions)
                .setHeader(localization.i18n("Actions"))
                .setAutoWidth(true)
                .setFlexGrow(0);
    }

    private HorizontalLayout actions(IdentityApplication application) {
        var edit = new Button(localization.i18n("Edit"), VaadinIcon.EDIT.create());
        edit.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        edit.addClickListener(event -> openForm(workspace, application));

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

    private void openForm(IdentityWorkspace workspace, IdentityApplication application) {
        var editing = application.getUniqueId() != null;
        var formApplication = editing ? copy(application) : application;

        var dialog = new Dialog();
        dialog.setHeaderTitle(localization.i18n(editing ? "Edit application" : "Create application"));
        dialog.setDraggable(true);
        dialog.setWidth("640px");

        var binder = new Binder<>(IdentityApplication.class);
        var name = new TextField(localization.i18n("Name"));
        name.setWidthFull();
        name.setRequiredIndicatorVisible(true);

        var data = new TextArea(localization.i18n("Data"));
        data.setWidthFull();

        binder.forField(name)
                .asRequired(localization.i18n("Name is required"))
                .withValidator(value -> !value.isBlank(), localization.i18n("Name is required"))
                .bind(IdentityApplication::getName, IdentityApplication::setName);
        binder.forField(data)
                .bind(IdentityApplication::getData, IdentityApplication::setData);
        binder.readBean(formApplication);

        var form = new FormLayout(name, data);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        var save = new Button(localization.i18n("Save"), event -> save(dialog, binder, workspace, formApplication));
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        var cancel = new Button(localization.i18n("Cancel"), event -> dialog.close());

        var footer = new HorizontalLayout(cancel, save);
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);

        dialog.add(new VerticalLayout(form));
        dialog.getFooter().add(footer);
        dialog.open();
    }

    private void save(
            Dialog dialog,
            Binder<IdentityApplication> binder,
            IdentityWorkspace workspace,
            IdentityApplication application
    ) {
        try {
            binder.writeBean(application);

            if (application.getUniqueId() == null) {
                workspaceService.createApplication(workspace.getUniqueId(), application);
            } else {
                applicationService.update(application);
            }
            dialog.close();
            refreshGrid();
            notify(localization.i18n("Application saved"), NotificationVariant.LUMO_SUCCESS);
        } catch (ValidationException ignored) {
            notify(localization.i18n("Fix validation errors"), NotificationVariant.LUMO_ERROR);
        } catch (Exception e) {
            notify(localization.i18n(e), NotificationVariant.LUMO_ERROR);
        }
    }

    private void confirmDelete(IdentityApplication application) {
        var dialog = new ConfirmDialog();
        dialog.setHeader(localization.i18n("Delete application"));
        dialog.setText(localization.i18n("Delete application confirmation"));
        dialog.setCancelable(true);
        dialog.setCancelText(localization.i18n("Cancel"));
        dialog.setConfirmText(localization.i18n("Delete"));
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(event -> delete(application));
        dialog.open();
    }

    private void delete(IdentityApplication application) {
        try {
            applicationService.delete(application.getUniqueId());
            refreshGrid();
            notify(localization.i18n("Application deleted"), NotificationVariant.LUMO_SUCCESS);
        } catch (Exception e) {
            notify(localization.i18n(e), NotificationVariant.LUMO_ERROR);
        }
    }

    private void refreshGrid() {
        var applications = applicationService.findByWorkspaceUniqueId(workspace.getUniqueId());

        grid.setItems(applications);
    }

    private IdentityWorkspace loadWorkspace(String workspaceId) {
        return workspaceService.getById(UniqueId.parse(workspaceId));
    }

    private String format(Instant instant) {
        return localization.formatDateTime(instant);
    }

    private IdentityApplication copy(IdentityApplication application) {
        return IdentityApplication.builder()
                .uniqueId(application.getUniqueId())
                .version(application.getVersion())
                .createdAt(application.getCreatedAt())
                .updatedAt(application.getUpdatedAt())
                .workspaceUniqueId(application.getWorkspaceUniqueId())
                .name(application.getName())
                .data(application.getData())
                .build();
    }

    private void notify(String message, NotificationVariant variant) {
        var notification = Notification.show(message, 3000, Notification.Position.TOP_END);
        notification.addThemeVariants(variant);
    }
}
