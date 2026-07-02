package vg.identity.frontend.vaadin.admin;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
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
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import vg.identity.frontend.vaadin.MainView;
import vg.identity.frontend.vaadin.Role;
import vg.identity.frontend.vaadin.service.LocalizationService;
import vg.identity.model.IdentityWorkspace;
import vg.identity.service.IdentityWorkspaceService;

@PageTitle("Workspaces")
@Route(value = "admin/workspaces", layout = MainView.class)
@RolesAllowed(Role.OWNER)
public class IdentityWorkspaces extends VerticalLayout {

    private final transient IdentityWorkspaceService workspaceService;
    private final LocalizationService localization;
    private final Grid<IdentityWorkspace> grid = new Grid<>(IdentityWorkspace.class, false);

    public IdentityWorkspaces(IdentityWorkspaceService workspaceService, LocalizationService localization) {
        this.workspaceService = workspaceService;
        this.localization = localization;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        configureGrid();

        var create = new Button(localization.i18n("Create"), VaadinIcon.PLUS.create());
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        create.addClickListener(event -> openForm(new IdentityWorkspace()));

        var toolbar = new HorizontalLayout(create);
        toolbar.setWidthFull();
        toolbar.setJustifyContentMode(FlexComponent.JustifyContentMode.END);

        add(toolbar, grid);
        expand(grid);

        refreshGrid();
    }

    private void configureGrid() {
        grid.setSizeFull();
        grid.addClassName("clickable-workspaces-grid");
        grid.setEmptyStateText(localization.i18n("No workspaces found"));

        grid.addColumn(IdentityWorkspace::getName)
                .setHeader(localization.i18n("Name"))
                .setSortable(true)
                .setAutoWidth(true);
        grid.addColumn(workspace -> localization.formatDateTime(workspace.getCreatedAt()))
                .setHeader(localization.i18n("Created"))
                .setSortable(true)
                .setComparator(IdentityWorkspace::getCreatedAt)
                .setAutoWidth(true);
        grid.addColumn(workspace -> localization.formatDateTime(workspace.getUpdatedAt()))
                .setHeader(localization.i18n("Updated"))
                .setSortable(true)
                .setComparator(IdentityWorkspace::getUpdatedAt)
                .setAutoWidth(true);
        grid.addItemClickListener(event -> UI.getCurrent().navigate(
                IdentityWorkspaceDetails.class,
                IdentityWorkspaceDetails.routeParameters(event.getItem())
        ));
    }

    private void refreshGrid() {
        grid.setItems(workspaceService.getAll());
    }

    private void openForm(IdentityWorkspace workspace) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(localization.i18n("Create workspace"));
        dialog.setDraggable(true);

        var binder = new Binder<>(IdentityWorkspace.class);
        var name = new TextField(localization.i18n("Name"));
        name.setWidthFull();
        name.setRequiredIndicatorVisible(true);

        var description = new TextArea(localization.i18n("Description"));
        description.setWidthFull();

        binder.forField(name)
                .asRequired(localization.i18n("Name is required"))
                .withValidator(value -> !value.isBlank(), localization.i18n("Name is required"))
                .bind(IdentityWorkspace::getName, IdentityWorkspace::setName);
        binder.forField(description)
                .withNullRepresentation("")
                .bind(IdentityWorkspace::getDescription, IdentityWorkspace::setDescription);
        binder.readBean(workspace);

        var form = new FormLayout(name, description);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        var save = new Button(localization.i18n("Save"), event -> save(dialog, binder, workspace));
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        var cancel = new Button(localization.i18n("Cancel"), event -> dialog.close());

        var footer = new HorizontalLayout(cancel, save);
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);

        dialog.add(new VerticalLayout(form));
        dialog.getFooter().add(footer);
        dialog.open();
    }

    private void save(Dialog dialog, Binder<IdentityWorkspace> binder, IdentityWorkspace workspace) {
        try {
            binder.writeBean(workspace);
            var saved = workspaceService.create(workspace);
            dialog.close();
            notify(localization.i18n("Workspace saved"), NotificationVariant.LUMO_SUCCESS);
            UI.getCurrent().navigate(IdentityWorkspaceDetails.class, IdentityWorkspaceDetails.routeParameters(saved));
        } catch (ValidationException ignored) {
            notify(localization.i18n("Fix validation errors"), NotificationVariant.LUMO_ERROR);
        } catch (Exception e) {
            notify(localization.i18n(e), NotificationVariant.LUMO_ERROR);
        }
    }

    private void notify(String message, NotificationVariant variant) {
        var notification = Notification.show(message, 3000, Notification.Position.TOP_END);
        notification.addThemeVariants(variant);
    }
}
