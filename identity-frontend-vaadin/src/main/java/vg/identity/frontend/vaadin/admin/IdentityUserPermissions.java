package vg.identity.frontend.vaadin.admin;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.treegrid.TreeGrid;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import vg.identity.frontend.vaadin.MainView;
import vg.identity.frontend.vaadin.Role;
import vg.identity.frontend.vaadin.service.LocalizationService;
import vg.identity.model.IdentityResourceType;
import vg.identity.model.IdentityUser;
import vg.identity.model.IdentityWorkspace;
import vg.identity.service.IdentityWorkspaceService;
import vg.identity.service.IdentityUserAuthorityService;
import vg.identity.service.IdentityUserServiceImpl;
import vg.unique.id.jpa.UniqueIdEntity;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@PageTitle("User permissions")
@Route(value = "admin/user-permissions", layout = MainView.class)
@RolesAllowed(Role.OWNER)
public class IdentityUserPermissions extends VerticalLayout {

    private final IdentityUserServiceImpl userService;
    private final IdentityWorkspaceService workspaceService;
    private final IdentityUserAuthorityService authorityService;
    private final LocalizationService localization;
    private final Grid<IdentityUser> usersGrid = new Grid<>(IdentityUser.class, false);
    private final DateTimeFormatter dateTimeFormatter;

    public IdentityUserPermissions(
            IdentityUserServiceImpl userService,
            IdentityWorkspaceService workspaceService,
            IdentityUserAuthorityService authorityService,
            LocalizationService localization
    ) {
        this.userService = userService;
        this.workspaceService = workspaceService;
        this.authorityService = authorityService;
        this.localization = localization;
        this.dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                .withLocale(localization.getCurrentLocale())
                .withZone(ZoneId.systemDefault());

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        configureUsersGrid();
        add(usersGrid);
        expand(usersGrid);
        refreshUsers();
    }

    private void configureUsersGrid() {
        usersGrid.setSizeFull();
        usersGrid.setEmptyStateText(localization.i18n("No users found"));
        usersGrid.addColumn(IdentityUser::getUsername)
                .setHeader(localization.i18n("Username"))
                .setSortable(true)
                .setAutoWidth(true);
        usersGrid.addColumn(user -> format(user.getCreatedAt()))
                .setHeader(localization.i18n("Created"))
                .setSortable(true)
                .setComparator(IdentityUser::getCreatedAt)
                .setAutoWidth(true);
        usersGrid.addComponentColumn(user -> {
                    var edit = new Button(localization.i18n("Edit permissions"), VaadinIcon.EDIT.create());
                    edit.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
                    edit.addClickListener(event -> openPermissionsDialog(user));
                    return edit;
                })
                .setHeader(localization.i18n("Actions"))
                .setAutoWidth(true)
                .setFlexGrow(0);
    }

    private void openAddPermissionDialog(IdentityUser user, TreeGrid<PermissionTreeItem> tree) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(localization.i18n("Create permission"));
        dialog.setDraggable(true);
        dialog.setWidth("560px");

        var resourceType = new Select<IdentityResourceType>();
        resourceType.setLabel(localization.i18n("Resource type"));
        resourceType.setItems(IdentityResourceType.values());
        resourceType.setItemLabelGenerator(type -> localization.i18n(type.name()));
        resourceType.setWidthFull();

        var resource = new ComboBox<IdentityWorkspace>(localization.i18n("Resource"));
        resource.setItemLabelGenerator(IdentityWorkspace::getName);
        resource.setWidthFull();
        resource.setRequiredIndicatorVisible(true);
        resource.setEnabled(false);

        var permissions = new CheckboxGroup<String>();
        permissions.setLabel(localization.i18n("Permissions"));
        permissions.setItemLabelGenerator(localization::i18n);
        permissions.setWidthFull();
        permissions.setRequiredIndicatorVisible(true);
        permissions.setEnabled(false);

        resourceType.addValueChangeListener(event -> {
            resource.clear();
            permissions.clear();
            permissions.setEnabled(false);
            permissions.setItems();
            resource.setEnabled(event.getValue() != null);

            if (event.getValue() == IdentityResourceType.WORKSPACE) {
                resource.setItems(workspaceService.getAll());
            }
            if (event.getValue() != null) {
                permissions.setItems(event.getValue().getPermissions());
            }
        });
        resource.addValueChangeListener(event ->
                permissions.setEnabled(event.getValue() != null && resourceType.getValue() != null)
        );

        var save = new Button(localization.i18n("Save"), event -> {
            var selectedPermissions = permissions.getValue();
            if (resourceType.getValue() == null || resource.getValue() == null || selectedPermissions.isEmpty()) {
                notify(localization.i18n("Fix validation errors"), NotificationVariant.LUMO_ERROR);
                return;
            }
            selectedPermissions.forEach(permission ->
                    authorityService.assignResourceAuthority(resource.getValue(), user, permission));
            dialog.close();
            refreshTree(user, tree);
            notify(localization.i18n("Permission saved"), NotificationVariant.LUMO_SUCCESS);
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        var cancel = new Button(localization.i18n("Cancel"), event -> dialog.close());
        var footer = new HorizontalLayout(cancel, save);
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);

        dialog.add(new VerticalLayout(resourceType, resource, permissions));
        dialog.getFooter().add(footer);
        dialog.open();
    }

    private void openPermissionsDialog(IdentityUser user) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(localization.i18n("Permissions") + ": " + user.getUsername());
        dialog.setDraggable(true);
        dialog.setWidth("760px");

        var tree = new TreeGrid<PermissionTreeItem>();
        tree.setWidthFull();
        tree.setHeight("420px");
        tree.addHierarchyColumn(PermissionTreeItem::name)
                .setHeader(localization.i18n("Resource"))
                .setAutoWidth(true);
        tree.addColumn(PermissionTreeItem::permissionName)
                .setHeader(localization.i18n("Permission"))
                .setAutoWidth(true);
        tree.addComponentColumn(item -> permissionCheckbox(user, item))
                .setHeader(localization.i18n("Granted"))
                .setAutoWidth(true)
                .setFlexGrow(0);
        tree.addSelectionListener(event -> event.getFirstSelectedItem()
                .filter(PermissionTreeItem::expandable)
                .ifPresent(tree::expand));

        refreshTree(user, tree);

        var add = new Button(localization.i18n("Add"), VaadinIcon.PLUS.create());
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        add.addClickListener(event -> openAddPermissionDialog(user, tree));

        var toolbar = new HorizontalLayout(add);
        toolbar.setWidthFull();
        toolbar.setJustifyContentMode(FlexComponent.JustifyContentMode.END);

        var close = new Button(localization.i18n("Close"), event -> dialog.close());
        dialog.add(toolbar, tree);
        dialog.getFooter().add(close);
        dialog.open();
    }

    private Component permissionCheckbox(IdentityUser user, PermissionTreeItem item) {
        if (!item.permission()) {
            return new HorizontalLayout();
        }

        var checkbox = new Checkbox(item.granted());
        checkbox.addValueChangeListener(event -> {
            if (event.getValue()) {
                authorityService.assignResourceAuthority(item.resource(), user, item.permissionName());
                item.setGranted(true);
                notify(localization.i18n("Permission saved"), NotificationVariant.LUMO_SUCCESS);
                return;
            }
            authorityService.revokeResourceAuthority(item.resource(), user, item.permissionName());
            item.setGranted(false);
            notify(localization.i18n("Permission removed"), NotificationVariant.LUMO_SUCCESS);
        });
        return checkbox;
    }

    private void refreshTree(IdentityUser user, TreeGrid<PermissionTreeItem> tree) {
        var roots = Arrays.stream(IdentityResourceType.values())
                .map(type -> PermissionTreeItem.root(type, localization.i18n(type.name())))
                .toList();
        tree.setItems(roots, item -> {
            if (item.root()) {
                return resources(user, item.type());
            }
            if (item.resourceNode()) {
                return permissionItems(item);
            }
            return List.of();
        });
    }

    private List<PermissionTreeItem> resources(IdentityUser user, IdentityResourceType type) {
        if (type != IdentityResourceType.WORKSPACE) {
            return List.of();
        }

        var treeItemByResourceUniqueId = new HashMap<Long, PermissionTreeItem>();
        authorityService.findByUserAndResourceType(user, type)
                .forEach(permission -> treeItemByResourceUniqueId
                        .computeIfAbsent(
                                permission.getResource().getUniqueId(),
                                ignored -> PermissionTreeItem.resource(
                                        type,
                                        permission.getResource(),
                                        permission.getResourceName(),
                                        new HashSet<>()
                                )
                        )
                        .permissions()
                        .add(permission.getPermissionName()));

        return treeItemByResourceUniqueId.values().stream().toList();
    }

    private List<PermissionTreeItem> permissionItems(PermissionTreeItem resource) {
        return Arrays.stream(resource.type().getPermissions())
                .map(permission -> PermissionTreeItem.permission(
                        resource.type(),
                        resource.resource(),
                        resource.resourceName(),
                        permission,
                        resource.permissions(),
                        resource.hasPermission(permission)
                ))
                .toList();
    }

    private void refreshUsers() {
        usersGrid.setItems(userService.findAll());
    }

    private String format(Instant instant) {
        return instant == null ? "" : dateTimeFormatter.format(instant);
    }

    private void notify(String message, NotificationVariant variant) {
        var notification = Notification.show(message, 3000, Notification.Position.TOP_END);
        notification.addThemeVariants(variant);
    }

    private static class PermissionTreeItem {
        private final IdentityResourceType type;
        private final UniqueIdEntity resource;
        private final String name;
        private final String resourceName;
        private final String permissionName;
        private final Set<String> permissions;
        private boolean granted;

        private PermissionTreeItem(
                IdentityResourceType type,
                UniqueIdEntity resource,
                String name,
                String resourceName,
                String permissionName,
                Set<String> permissions,
                boolean granted
        ) {
            this.type = type;
            this.resource = resource;
            this.name = name;
            this.resourceName = resourceName;
            this.permissionName = permissionName;
            this.permissions = permissions;
            this.granted = granted;
        }

        private static PermissionTreeItem root(IdentityResourceType type, String name) {
            return new PermissionTreeItem(type, null, name, "", "", Set.of(), false);
        }

        private static PermissionTreeItem resource(
                IdentityResourceType type,
                UniqueIdEntity resource,
                String resourceName,
                Set<String> permissions
        ) {
            return new PermissionTreeItem(type, resource, resourceName, resourceName, "", permissions, false);
        }

        private static PermissionTreeItem permission(
                IdentityResourceType type,
                UniqueIdEntity resource,
                String resourceName,
                String permissionName,
                Set<String> permissions,
                boolean granted
        ) {
            return new PermissionTreeItem(type, resource, "", resourceName, permissionName, permissions, granted);
        }

        private IdentityResourceType type() {
            return type;
        }

        private UniqueIdEntity resource() {
            return resource;
        }

        private String name() {
            return name;
        }

        private String resourceName() {
            return resourceName;
        }

        private String permissionName() {
            return permissionName;
        }

        private Set<String> permissions() {
            return permissions;
        }

        private boolean root() {
            return resource == null;
        }

        private boolean resourceNode() {
            return resource != null && permissionName.isEmpty();
        }

        private boolean permission() {
            return resource != null && !permissionName.isEmpty();
        }

        private boolean expandable() {
            return root() || resourceNode();
        }

        private boolean hasPermission(String permission) {
            return permissions.contains(permission);
        }

        private boolean granted() {
            return granted;
        }

        private void setGranted(boolean granted) {
            this.granted = granted;
            if (granted) {
                permissions.add(permissionName);
            } else {
                permissions.remove(permissionName);
            }
        }
    }
}
