package vg.identity.frontend.vaadin.admin;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.treegrid.TreeGrid;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import vg.identity.frontend.vaadin.MainView;
import vg.identity.frontend.vaadin.Role;
import vg.identity.frontend.vaadin.service.LocalizationService;
import vg.identity.model.IdentityPermission;
import vg.identity.model.IdentityRoleTemplate;
import vg.identity.service.IdentityPermissionService;
import vg.identity.service.IdentityRoleTemplateService;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;

@PageTitle("Role templates")
@Route(value = "admin/role-templates", layout = MainView.class)
@RolesAllowed(Role.OWNER)
public class IdentityRoleTemplates extends VerticalLayout {

    private final transient IdentityRoleTemplateService roleTemplateService;
    private final transient IdentityPermissionService permissionService;
    private final LocalizationService localization;
    private final TreeGrid<RoleTemplateTreeItem> grid = new TreeGrid<>();

    public IdentityRoleTemplates(
            IdentityRoleTemplateService roleTemplateService,
            IdentityPermissionService permissionService,
            LocalizationService localization
    ) {
        this.roleTemplateService = roleTemplateService;
        this.permissionService = permissionService;
        this.localization = localization;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        configureGrid();

        var create = new Button(localization.i18n("Create"), VaadinIcon.PLUS.create());
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        create.addClickListener(event -> openForm(new IdentityRoleTemplate()));

        var toolbar = new HorizontalLayout(create);
        toolbar.setWidthFull();
        toolbar.setJustifyContentMode(FlexComponent.JustifyContentMode.END);

        add(toolbar, grid);
        expand(grid);

        refreshGrid();
    }

    private void configureGrid() {
        grid.setSizeFull();
        grid.setEmptyStateText(localization.i18n("No role templates found"));

        grid.addHierarchyColumn(RoleTemplateTreeItem::name)
                .setHeader(localization.i18n("Name"))
                .setSortable(true)
                .setAutoWidth(true);
        grid.addColumn(RoleTemplateTreeItem::description)
                .setHeader(localization.i18n("Description"))
                .setSortable(true)
                .setAutoWidth(true);
        grid.addComponentColumn(this::permissionCheckbox)
                .setHeader(localization.i18n("Granted"))
                .setAutoWidth(true)
                .setFlexGrow(0);
        grid.addColumn(item -> format(item.createdAt()))
                .setHeader(localization.i18n("Created"))
                .setSortable(true)
                .setAutoWidth(true);
        grid.addColumn(item -> format(item.updatedAt()))
                .setHeader(localization.i18n("Updated"))
                .setSortable(true)
                .setAutoWidth(true);
        grid.addComponentColumn(this::actions)
                .setHeader(localization.i18n("Actions"))
                .setAutoWidth(true)
                .setFlexGrow(0);
        grid.addSelectionListener(event -> event.getFirstSelectedItem()
                .filter(RoleTemplateTreeItem::root)
                .ifPresent(grid::expand));
    }

    private Component permissionCheckbox(RoleTemplateTreeItem item) {
        if (item.root()) {
            return new HorizontalLayout();
        }

        var checkbox = new Checkbox(item.granted());
        checkbox.addValueChangeListener(event -> {
            try {
                var updated = event.getValue()
                        ? roleTemplateService.addPermission(item.templateId(), item.permissionName())
                        : roleTemplateService.removePermission(item.templateId(), item.permissionName());
                item.updateTemplate(updated);
                item.setGranted(event.getValue());
                notify(localization.i18n("Role template saved"), NotificationVariant.LUMO_SUCCESS);
            } catch (Exception e) {
                checkbox.setValue(item.granted());
                notify(localization.i18n(e), NotificationVariant.LUMO_ERROR);
            }
        });
        return checkbox;
    }

    private HorizontalLayout actions(RoleTemplateTreeItem item) {
        if (!item.root()) {
            return new HorizontalLayout();
        }

        var template = item.template();
        var edit = new Button(localization.i18n("Edit"), VaadinIcon.EDIT.create());
        edit.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        edit.addClickListener(event -> openForm(template));

        var delete = new Button(localization.i18n("Delete"), VaadinIcon.TRASH.create());
        delete.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        delete.addClickListener(event -> confirmDelete(template));

        var actions = new HorizontalLayout(edit, delete);
        actions.setPadding(false);
        actions.setSpacing(true);
        return actions;
    }

    private void openForm(IdentityRoleTemplate template) {
        var editing = template.getId() != null;
        var formTemplate = editing ? copy(template) : template;

        var dialog = new Dialog();
        dialog.setHeaderTitle(localization.i18n(editing ? "Edit role template" : "Create role template"));
        dialog.setDraggable(true);
        dialog.setWidth("640px");

        var binder = new Binder<>(IdentityRoleTemplate.class);
        var name = new TextField(localization.i18n("Name"));
        name.setWidthFull();
        name.setRequiredIndicatorVisible(true);
        name.setEnabled(!editing);

        var description = new TextArea(localization.i18n("Description"));
        description.setWidthFull();

        binder.forField(name)
                .asRequired(localization.i18n("Name is required"))
                .withValidator(value -> !value.isBlank(), localization.i18n("Name is required"))
                .bind(IdentityRoleTemplate::getName, IdentityRoleTemplate::setName);
        binder.forField(description)
                .bind(IdentityRoleTemplate::getDescription, IdentityRoleTemplate::setDescription);
        binder.readBean(formTemplate);

        var form = new FormLayout(name, description);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        var save = new Button(localization.i18n("Save"), event -> save(dialog, binder, formTemplate));
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
            Binder<IdentityRoleTemplate> binder,
            IdentityRoleTemplate template
    ) {
        try {
            binder.writeBean(template);

            if (template.getId() == null) {
                roleTemplateService.create(template);
            } else {
                roleTemplateService.update(template);
            }
            dialog.close();
            refreshGrid();
            notify(localization.i18n("Role template saved"), NotificationVariant.LUMO_SUCCESS);
        } catch (ValidationException ignored) {
            notify(localization.i18n("Fix validation errors"), NotificationVariant.LUMO_ERROR);
        } catch (Exception e) {
            notify(localization.i18n(e), NotificationVariant.LUMO_ERROR);
        }
    }

    private void confirmDelete(IdentityRoleTemplate template) {
        var dialog = new ConfirmDialog();
        dialog.setHeader(localization.i18n("Delete role template"));
        dialog.setText(localization.i18n("Delete role template confirmation"));
        dialog.setCancelable(true);
        dialog.setCancelText(localization.i18n("Cancel"));
        dialog.setConfirmText(localization.i18n("Delete"));
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(event -> delete(template));
        dialog.open();
    }

    private void delete(IdentityRoleTemplate template) {
        try {
            roleTemplateService.delete(template.getId());
            refreshGrid();
            notify(localization.i18n("Role template deleted"), NotificationVariant.LUMO_SUCCESS);
        } catch (Exception e) {
            notify(localization.i18n(e), NotificationVariant.LUMO_ERROR);
        }
    }

    private void refreshGrid() {
        var permissions = permissionService.getAll().stream()
                .map(IdentityPermission::getName)
                .sorted()
                .toList();
        var roots = roleTemplateService.getAll().stream()
                .map(RoleTemplateTreeItem::root)
                .toList();
        grid.setItems(roots, item -> item.root() ? permissionItems(item.template(), permissions) : List.of());
    }

    private List<RoleTemplateTreeItem> permissionItems(IdentityRoleTemplate template, List<String> permissions) {
        return permissions.stream()
                .map(permission -> RoleTemplateTreeItem.permission(template, permission))
                .toList();
    }

    private String format(Instant instant) {
        return localization.formatDateTime(instant);
    }

    private IdentityRoleTemplate copy(IdentityRoleTemplate template) {
        return IdentityRoleTemplate.builder()
                .id(template.getId())
                .version(template.getVersion())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .name(template.getName())
                .description(template.getDescription())
                .permissions(new HashSet<>(template.getPermissions()))
                .build();
    }

    private void notify(String message, NotificationVariant variant) {
        var notification = Notification.show(message, 3000, Notification.Position.TOP_END);
        notification.addThemeVariants(variant);
    }

    private static class RoleTemplateTreeItem {
        private IdentityRoleTemplate template;
        private final String permissionName;
        private boolean granted;

        private RoleTemplateTreeItem(IdentityRoleTemplate template, String permissionName, boolean granted) {
            this.template = template;
            this.permissionName = permissionName;
            this.granted = granted;
        }

        private static RoleTemplateTreeItem root(IdentityRoleTemplate template) {
            return new RoleTemplateTreeItem(template, "", false);
        }

        private static RoleTemplateTreeItem permission(IdentityRoleTemplate template, String permissionName) {
            return new RoleTemplateTreeItem(template, permissionName, template.getPermissions().contains(permissionName));
        }

        private IdentityRoleTemplate template() {
            return template;
        }

        private Long templateId() {
            return template.getId();
        }

        private String permissionName() {
            return permissionName;
        }

        private String name() {
            return root() ? template.getName() : permissionName;
        }

        private String description() {
            return root() ? template.getDescription() : "";
        }

        private Instant createdAt() {
            return root() ? template.getCreatedAt() : null;
        }

        private Instant updatedAt() {
            return root() ? template.getUpdatedAt() : null;
        }

        private boolean root() {
            return permissionName.isEmpty();
        }

        private boolean granted() {
            return granted;
        }

        private void setGranted(boolean granted) {
            this.granted = granted;
        }

        private void updateTemplate(IdentityRoleTemplate template) {
            this.template = template;
        }
    }
}
