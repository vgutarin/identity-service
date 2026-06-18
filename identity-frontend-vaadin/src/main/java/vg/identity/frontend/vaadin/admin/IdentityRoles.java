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
import vg.identity.model.IdentityRole;
import vg.identity.model.IdentityWorkspace;
import vg.identity.service.IdentityPermissionService;
import vg.identity.service.IdentityRoleService;
import vg.identity.service.IdentityWorkspaceService;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@PageTitle("Roles")
@Route(value = "admin/roles", layout = MainView.class)
@RolesAllowed(Role.OWNER)
public class IdentityRoles extends VerticalLayout {

    private final transient IdentityWorkspaceService workspaceService;
    private final transient IdentityRoleService roleService;
    private final transient IdentityPermissionService permissionService;
    private final LocalizationService localization;
    private final TreeGrid<RoleTreeItem> grid = new TreeGrid<>();

    public IdentityRoles(
            IdentityWorkspaceService workspaceService,
            IdentityRoleService roleService,
            IdentityPermissionService permissionService,
            LocalizationService localization
    ) {
        this.workspaceService = workspaceService;
        this.roleService = roleService;
        this.permissionService = permissionService;
        this.localization = localization;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        configureGrid();

        add(grid);
        expand(grid);

        refreshGrid();
    }

    private void configureGrid() {
        grid.setSizeFull();
        grid.setEmptyStateText(localization.i18n("No roles found"));

        grid.addHierarchyColumn(RoleTreeItem::name)
                .setHeader(localization.i18n("Name"))
                .setSortable(true)
                .setAutoWidth(true);
        grid.addColumn(RoleTreeItem::description)
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
                .filter(RoleTreeItem::expandable)
                .ifPresent(grid::expand));
    }

    private Component permissionCheckbox(RoleTreeItem item) {
        if (!item.permission()) {
            return new HorizontalLayout();
        }

        var checkbox = new Checkbox(item.granted());
        checkbox.addValueChangeListener(event -> {
            try {
                var updated = event.getValue()
                        ? roleService.addPermission(item.roleId(), item.permissionName())
                        : roleService.removePermission(item.roleId(), item.permissionName());
                item.updateRole(updated);
                item.setGranted(event.getValue());
                notify(localization.i18n("Role saved"), NotificationVariant.LUMO_SUCCESS);
            } catch (Exception e) {
                checkbox.setValue(item.granted());
                notify(localization.i18n(e), NotificationVariant.LUMO_ERROR);
            }
        });
        return checkbox;
    }

    private HorizontalLayout actions(RoleTreeItem item) {
        if (item.workspace()) {
            var add = new Button(localization.i18n("Add role"), VaadinIcon.PLUS.create());
            add.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
            add.addClickListener(event -> openForm(item.workspaceModel(), new IdentityRole()));
            return actionLayout(add);
        }
        if (!item.role()) {
            return new HorizontalLayout();
        }

        var role = item.roleModel();
        var edit = new Button(localization.i18n("Edit"), VaadinIcon.EDIT.create());
        edit.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        edit.addClickListener(event -> openForm(item.workspaceModel(), role));

        var delete = new Button(localization.i18n("Delete"), VaadinIcon.TRASH.create());
        delete.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        delete.addClickListener(event -> confirmDelete(role));

        return actionLayout(edit, delete);
    }

    private HorizontalLayout actionLayout(Component... actions) {
        var layout = new HorizontalLayout(actions);
        layout.setPadding(false);
        layout.setSpacing(true);
        return layout;
    }

    private void openForm(IdentityWorkspace workspace, IdentityRole role) {
        var editing = role.getId() != null;
        var formRole = editing ? copy(role) : role;

        var dialog = new Dialog();
        dialog.setHeaderTitle(localization.i18n(editing ? "Edit role" : "Create role"));
        dialog.setDraggable(true);
        dialog.setWidth("640px");

        var binder = new Binder<>(IdentityRole.class);
        var name = new TextField(localization.i18n("Name"));
        name.setWidthFull();
        name.setRequiredIndicatorVisible(true);
        name.setEnabled(!editing);

        var description = new TextArea(localization.i18n("Description"));
        description.setWidthFull();

        binder.forField(name)
                .asRequired(localization.i18n("Name is required"))
                .withValidator(value -> !value.isBlank(), localization.i18n("Name is required"))
                .bind(IdentityRole::getName, IdentityRole::setName);
        binder.forField(description)
                .bind(IdentityRole::getDescription, IdentityRole::setDescription);
        binder.readBean(formRole);

        var form = new FormLayout(name, description);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        var save = new Button(localization.i18n("Save"), event -> save(dialog, binder, workspace, formRole));
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
            Binder<IdentityRole> binder,
            IdentityWorkspace workspace,
            IdentityRole role
    ) {
        try {
            binder.writeBean(role);

            if (role.getId() == null) {
                workspaceService.addRole(workspace.getUniqueId().value(), role);
            } else {
                roleService.update(role);
            }
            dialog.close();
            refreshGrid();
            notify(localization.i18n("Role saved"), NotificationVariant.LUMO_SUCCESS);
        } catch (ValidationException ignored) {
            notify(localization.i18n("Fix validation errors"), NotificationVariant.LUMO_ERROR);
        } catch (Exception e) {
            notify(localization.i18n(e), NotificationVariant.LUMO_ERROR);
        }
    }

    private void confirmDelete(IdentityRole role) {
        var dialog = new ConfirmDialog();
        dialog.setHeader(localization.i18n("Delete role"));
        dialog.setText(localization.i18n("Delete role confirmation"));
        dialog.setCancelable(true);
        dialog.setCancelText(localization.i18n("Cancel"));
        dialog.setConfirmText(localization.i18n("Delete"));
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(event -> delete(role));
        dialog.open();
    }

    private void delete(IdentityRole role) {
        try {
            roleService.delete(role.getId());
            refreshGrid();
            notify(localization.i18n("Role deleted"), NotificationVariant.LUMO_SUCCESS);
        } catch (Exception e) {
            notify(localization.i18n(e), NotificationVariant.LUMO_ERROR);
        }
    }

    private void refreshGrid() {
        var permissions = permissionService.getAll().stream()
                .map(IdentityPermission::getName)
                .sorted()
                .toList();
        var rolesByWorkspaceUniqueId = roleService.getAll().stream()
                .filter(role -> role.getWorkspaceUniqueId() != null)
                .collect(Collectors.groupingBy(IdentityRole::getWorkspaceUniqueId));
        var roots = workspaceService.getAll().stream()
                .map(workspace -> RoleTreeItem.workspace(workspace, rolesByWorkspaceUniqueId, permissions))
                .toList();

        grid.setItems(roots, RoleTreeItem::children);
    }

    private String format(Instant instant) {
        return localization.formatDateTime(instant);
    }

    private IdentityRole copy(IdentityRole role) {
        return IdentityRole.builder()
                .id(role.getId())
                .version(role.getVersion())
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .name(role.getName())
                .description(role.getDescription())
                .workspaceUniqueId(role.getWorkspaceUniqueId())
                .permissions(new HashSet<>(role.getPermissions()))
                .build();
    }

    private void notify(String message, NotificationVariant variant) {
        var notification = Notification.show(message, 3000, Notification.Position.TOP_END);
        notification.addThemeVariants(variant);
    }

    private static class RoleTreeItem {
        private final IdentityWorkspace workspace;
        private IdentityRole role;
        private final String permissionName;
        private final List<RoleTreeItem> children;
        private boolean granted;

        private RoleTreeItem(
                IdentityWorkspace workspace,
                IdentityRole role,
                String permissionName,
                List<RoleTreeItem> children,
                boolean granted
        ) {
            this.workspace = workspace;
            this.role = role;
            this.permissionName = permissionName;
            this.children = children;
            this.granted = granted;
        }

        private static RoleTreeItem workspace(
                IdentityWorkspace workspace,
                Map<Long, List<IdentityRole>> rolesByWorkspaceUniqueId,
                List<String> permissions
        ) {
            var roles = rolesByWorkspaceUniqueId
                    .getOrDefault(workspace.getUniqueId().value(), List.of())
                    .stream()
                    .map(role -> role(workspace, role, permissions))
                    .toList();
            return new RoleTreeItem(workspace, null, "", roles, false);
        }

        private static RoleTreeItem role(IdentityWorkspace workspace, IdentityRole role, List<String> permissions) {
            var permissionItems = permissions.stream()
                    .map(permission -> permission(workspace, role, permission))
                    .toList();
            return new RoleTreeItem(workspace, role, "", permissionItems, false);
        }

        private static RoleTreeItem permission(IdentityWorkspace workspace, IdentityRole role, String permissionName) {
            return new RoleTreeItem(
                    workspace,
                    role,
                    permissionName,
                    List.of(),
                    role.getPermissions().contains(permissionName)
            );
        }

        private IdentityWorkspace workspaceModel() {
            return workspace;
        }

        private IdentityRole roleModel() {
            return role;
        }

        private Long roleId() {
            return role.getId();
        }

        private String permissionName() {
            return permissionName;
        }

        private String name() {
            if (workspace()) {
                return workspace.getName();
            }
            if (role()) {
                return role.getName();
            }
            return permissionName;
        }

        private String description() {
            return role() ? role.getDescription() : "";
        }

        private Instant createdAt() {
            if (workspace()) {
                return workspace.getCreatedAt();
            }
            if (role()) {
                return role.getCreatedAt();
            }
            return null;
        }

        private Instant updatedAt() {
            if (workspace()) {
                return workspace.getUpdatedAt();
            }
            if (role()) {
                return role.getUpdatedAt();
            }
            return null;
        }

        private List<RoleTreeItem> children() {
            return children;
        }

        private boolean workspace() {
            return role == null && permissionName.isEmpty();
        }

        private boolean role() {
            return role != null && permissionName.isEmpty();
        }

        private boolean permission() {
            return role != null && !permissionName.isEmpty();
        }

        private boolean expandable() {
            return workspace() || role();
        }

        private boolean granted() {
            return granted;
        }

        private void setGranted(boolean granted) {
            this.granted = granted;
        }

        private void updateRole(IdentityRole role) {
            this.role = role;
        }
    }
}
