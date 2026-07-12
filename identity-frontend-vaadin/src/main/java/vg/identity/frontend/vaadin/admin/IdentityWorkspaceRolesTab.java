package vg.identity.frontend.vaadin.admin;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.treegrid.TreeGrid;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import vg.identity.frontend.vaadin.service.LocalizationService;
import vg.identity.frontend.vaadin.ui.Dialogs;
import vg.identity.frontend.vaadin.ui.Notifications;
import vg.identity.model.IdentityPermission;
import vg.identity.model.IdentityRole;
import vg.identity.model.IdentityWorkspace;
import vg.identity.service.IdentityPermissionService;
import vg.identity.service.IdentityRoleService;
import vg.identity.service.IdentityWorkspaceService;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

class IdentityWorkspaceRolesTab extends VerticalLayout {

    private final transient IdentityWorkspaceService workspaceService;
    private final transient IdentityRoleService roleService;
    private final transient IdentityPermissionService permissionService;
    private final LocalizationService localization;
    private final HorizontalLayout actions = new HorizontalLayout();
    private final TreeGrid<RoleTreeItem> grid = new TreeGrid<>();
    private IdentityWorkspace workspace;

    IdentityWorkspaceRolesTab(
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
                .setComparator(Comparator.comparing(RoleTreeItem::createdAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .setAutoWidth(true);
        grid.addColumn(item -> format(item.updatedAt()))
                .setHeader(localization.i18n("Updated"))
                .setSortable(true)
                .setComparator(Comparator.comparing(RoleTreeItem::updatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .setAutoWidth(true);
        grid.addComponentColumn(this::rowActions)
                .setHeader(localization.i18n("Actions"))
                .setAutoWidth(true)
                .setFlexGrow(0);
        grid.addSelectionListener(event -> event.getFirstSelectedItem()
                .filter(RoleTreeItem::expandable)
                .ifPresent(grid::expand));
    }

    private void refreshActions() {
        actions.removeAll();
        actions.setVisible(workspace != null);
        if (workspace == null) {
            return;
        }

        var add = new Button(localization.i18n("Add role"), VaadinIcon.PLUS.create());
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        add.addClickListener(event -> openForm(new IdentityRole()));

        actions.add(add);
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
                Notifications.success(localization.i18n("Role saved"));
            } catch (Exception e) {
                checkbox.setValue(item.granted());
                Notifications.error(localization.i18n(e));
            }
        });
        return checkbox;
    }

    private HorizontalLayout rowActions(RoleTreeItem item) {
        if (!item.role()) {
            return new HorizontalLayout();
        }

        var role = item.roleModel();
        var edit = new Button(localization.i18n("Edit"), VaadinIcon.EDIT.create());
        edit.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        edit.addClickListener(event -> openForm(role));

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

    private void openForm(IdentityRole role) {
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

        var save = new Button(localization.i18n("Save"), event -> save(dialog, binder, formRole));
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        var cancel = new Button(localization.i18n("Cancel"), event -> dialog.close());

        dialog.add(new VerticalLayout(form));
        dialog.getFooter().add(Dialogs.footer(cancel, save));
        dialog.open();
    }

    private void save(Dialog dialog, Binder<IdentityRole> binder, IdentityRole role) {
        try {
            binder.writeBean(role);

            if (role.getId() == null) {
                workspaceService.createRole(workspace.getUniqueId(), role);
            } else {
                roleService.update(role);
            }
            dialog.close();
            refreshGrid();
            Notifications.success(localization.i18n("Role saved"));
        } catch (ValidationException ignored) {
            Notifications.error(localization.i18n("Fix validation errors"));
        } catch (Exception e) {
            Notifications.error(localization.i18n(e));
        }
    }

    private void confirmDelete(IdentityRole role) {
        Dialogs.confirmDelete(
                localization,
                "Delete role",
                "Delete role confirmation",
                () -> delete(role)
        );
    }

    private void delete(IdentityRole role) {
        try {
            roleService.delete(role.getId());
            refreshGrid();
            Notifications.success(localization.i18n("Role deleted"));
        } catch (Exception e) {
            Notifications.error(localization.i18n(e));
        }
    }

    private void refreshGrid() {
        if (workspace == null) {
            grid.setItems(List.of(), RoleTreeItem::children);
            return;
        }

        var permissions = permissionService.getAll().stream()
                .map(IdentityPermission::getName)
                .sorted()
                .toList();
        var roles = roleService.getAll().stream()
                .filter(role -> Long.valueOf(workspace.getUniqueId().getLongValue()).equals(role.getWorkspaceUniqueId()))
                .map(role -> RoleTreeItem.role(workspace, role, permissions))
                .toList();

        grid.setItems(roles, RoleTreeItem::children);
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
            if (role()) {
                return role.getName();
            }
            return permissionName;
        }

        private String description() {
            return role() ? role.getDescription() : "";
        }

        private Instant createdAt() {
            if (role()) {
                return role.getCreatedAt();
            }
            return null;
        }

        private Instant updatedAt() {
            if (role()) {
                return role.getUpdatedAt();
            }
            return null;
        }

        private List<RoleTreeItem> children() {
            return children;
        }

        private boolean role() {
            return role != null && permissionName.isEmpty();
        }

        private boolean permission() {
            return role != null && !permissionName.isEmpty();
        }

        private boolean expandable() {
            return role();
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
