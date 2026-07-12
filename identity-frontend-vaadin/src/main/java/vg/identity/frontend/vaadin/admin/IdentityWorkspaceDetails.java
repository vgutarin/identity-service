package vg.identity.frontend.vaadin.admin;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParam;
import com.vaadin.flow.router.RouteParameters;
import jakarta.annotation.security.RolesAllowed;
import vg.identity.frontend.vaadin.MainView;
import vg.identity.frontend.vaadin.Role;
import vg.identity.frontend.vaadin.service.LocalizationService;
import vg.identity.frontend.vaadin.ui.Dialogs;
import vg.identity.frontend.vaadin.ui.Notifications;
import vg.identity.model.IdentityWorkspace;
import vg.identity.service.EmailService;
import vg.identity.service.IdentityApplicationService;
import vg.identity.service.IdentityPermissionService;
import vg.identity.service.IdentityRoleService;
import vg.identity.service.IdentityWorkspaceService;
import vg.unique.id.model.UniqueId;

@PageTitle("Workspace details")
@Route(value = "admin/workspaces/:workspaceId", layout = MainView.class)
@RolesAllowed(Role.OWNER)
public class IdentityWorkspaceDetails extends VerticalLayout implements BeforeEnterObserver {

    private static final String WORKSPACE_ID_PARAMETER = "workspaceId";
    private static final String TAB_PARAMETER = "tab";
    private static final String APPLICATIONS_TAB = "applications";
    private static final String ROLES_TAB = "roles";
    private static final String USERS_TAB = "users";

    private final transient IdentityWorkspaceService workspaceService;
    private final LocalizationService localization;
    private final HorizontalLayout toolbar = new HorizontalLayout();
    private final H2 name = new H2();
    private final TextArea description = new TextArea();
    private final Span createdAt = new Span();
    private final Span updatedAt = new Span();
    private final Tabs managementTabs = new Tabs();
    private final IdentityWorkspaceApplicationsTab applicationsContent;
    private final IdentityWorkspaceRolesTab rolesContent;
    private final IdentityWorkspaceUsersTab usersContent;
    private Tab applicationsTab;
    private Tab rolesTab;
    private Tab usersTab;
    private IdentityWorkspace workspace;
    private String selectedManagementTab = APPLICATIONS_TAB;

    public IdentityWorkspaceDetails(
            IdentityWorkspaceService workspaceService,
            IdentityApplicationService applicationService,
            IdentityRoleService roleService,
            IdentityPermissionService permissionService,
            EmailService emailService,
            LocalizationService localization
    ) {
        this.workspaceService = workspaceService;
        this.localization = localization;
        applicationsContent = new IdentityWorkspaceApplicationsTab(applicationService, localization);
        rolesContent = new IdentityWorkspaceRolesTab(workspaceService, roleService, permissionService, localization);
        usersContent = new IdentityWorkspaceUsersTab(workspaceService, emailService, localization);

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        configureToolbar();
        configureDetails();
        configureManagementTabs();

        add(toolbar, name, detailsLayout(), managementTabs, applicationsContent, rolesContent, usersContent);
        expand(applicationsContent, rolesContent, usersContent);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        workspace = event.getRouteParameters()
                .get(WORKSPACE_ID_PARAMETER)
                .map(this::loadWorkspace)
                .orElseThrow();
        selectedManagementTab = event.getLocation()
                .getQueryParameters()
                .getSingleParameter(TAB_PARAMETER)
                .filter(this::isValidTab)
                .orElse(APPLICATIONS_TAB);
        refresh();
    }

    private void configureToolbar() {
        toolbar.setWidthFull();
        toolbar.setAlignItems(FlexComponent.Alignment.CENTER);
        toolbar.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
    }

    private void configureDetails() {
        name.getStyle().set("margin", "0");
        description.setLabel(localization.i18n("Description"));
        description.setWidthFull();
        description.setReadOnly(true);
    }

    private void configureManagementTabs() {
        applicationsTab = new Tab(localization.i18n("Applications"));
        rolesTab = new Tab(localization.i18n("Roles"));
        usersTab = new Tab(localization.i18n("Users"));

        managementTabs.add(applicationsTab, rolesTab, usersTab);
        managementTabs.setWidthFull();
        managementTabs.setSelectedTab(applicationsTab);
        managementTabs.addSelectedChangeListener(event -> {
            selectedManagementTab = selectedTabKey();
            updateSelectedTabContent();
            if (event.isFromClient()) {
                updateSelectedTabUrl();
            }
        });
    }

    private FormLayout detailsLayout() {
        var layout = new FormLayout(
                description,
                new HorizontalLayout(createdAt, updatedAt)
        );
        layout.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        layout.setWidthFull();
        return layout;
    }

    private void refresh() {
        refreshToolbar();
        refreshManagementTabs();
        applicationsContent.setWorkspace(workspace);
        rolesContent.setWorkspace(workspace);
        usersContent.setWorkspace(workspace);
        name.setText(workspace.getName());
        description.setValue(workspace.getDescription() == null ? "" : workspace.getDescription());
        description.setPlaceholder(localization.i18n("No description"));
        createdAt.setText(localization.i18n("Created") + ": " + localization.formatDateTime(workspace.getCreatedAt()));
        updatedAt.setText(localization.i18n("Updated") + ": " + localization.formatDateTime(workspace.getUpdatedAt()));
    }

    private void refreshToolbar() {
        toolbar.removeAll();

        var back = new Button(localization.i18n("Workspaces"), VaadinIcon.ARROW_LEFT.create());
        back.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        back.addClassName("navigation-back-button");
        back.addClickListener(event -> UI.getCurrent().navigate(IdentityWorkspaces.class));

        var edit = new Button(localization.i18n("Edit"), VaadinIcon.EDIT.create());
        edit.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        edit.addClickListener(event -> openForm(workspace));

        var delete = new Button(localization.i18n("Delete"), VaadinIcon.TRASH.create());
        delete.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        delete.addClickListener(event -> confirmDelete(workspace));

        toolbar.add(back, edit, delete);
        toolbar.expand(back);
    }

    private void refreshManagementTabs() {
        managementTabs.setSelectedTab(tabFor(selectedManagementTab));
        updateSelectedTabContent();
    }

    private void updateSelectedTabContent() {
        var applicationsSelected = managementTabs.getSelectedTab() == applicationsTab;
        var rolesSelected = managementTabs.getSelectedTab() == rolesTab;
        var usersSelected = managementTabs.getSelectedTab() == usersTab;

        applicationsContent.setVisible(applicationsSelected);
        rolesContent.setVisible(rolesSelected);
        usersContent.setVisible(usersSelected);

        if (rolesSelected) {
            rolesContent.refresh();
        }
        if (usersSelected) {
            usersContent.refresh();
        }
    }

    private boolean isValidTab(String tab) {
        return APPLICATIONS_TAB.equals(tab)
                || ROLES_TAB.equals(tab)
                || USERS_TAB.equals(tab);
    }

    private Tab tabFor(String tab) {
        return switch (tab) {
            case ROLES_TAB -> rolesTab;
            case USERS_TAB -> usersTab;
            default -> applicationsTab;
        };
    }

    private String selectedTabKey() {
        if (managementTabs.getSelectedTab() == rolesTab) {
            return ROLES_TAB;
        }
        if (managementTabs.getSelectedTab() == usersTab) {
            return USERS_TAB;
        }
        return APPLICATIONS_TAB;
    }

    private void updateSelectedTabUrl() {
        UI.getCurrent().getPage().executeJs(
                "const url = new URL(window.location.href);"
                        + "url.searchParams.set($0, $1);"
                        + "window.history.replaceState(null, '', url);",
                TAB_PARAMETER,
                selectedManagementTab
        );
    }

    private void openForm(IdentityWorkspace target) {
        var editing = target.getUniqueId() != null;
        var formWorkspace = editing ? copy(target) : target;

        var dialog = new Dialog();
        dialog.setHeaderTitle(localization.i18n(editing ? "Edit workspace" : "Create workspace"));
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
        binder.readBean(formWorkspace);

        var form = new FormLayout(name, description);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        var save = new Button(localization.i18n("Save"), event -> save(dialog, binder, formWorkspace));
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        var cancel = new Button(localization.i18n("Cancel"), event -> dialog.close());

        dialog.add(new VerticalLayout(form));
        dialog.getFooter().add(Dialogs.footer(cancel, save));
        dialog.open();
    }

    private void save(Dialog dialog, Binder<IdentityWorkspace> binder, IdentityWorkspace target) {
        try {
            binder.writeBean(target);
            var saved = target.getUniqueId() == null
                    ? workspaceService.create(target)
                    : workspaceService.update(target);
            dialog.close();
            Notifications.success(localization.i18n("Workspace saved"));
            UI.getCurrent().navigate(IdentityWorkspaceDetails.class, routeParameters(saved));
        } catch (ValidationException ignored) {
            Notifications.error(localization.i18n("Fix validation errors"));
        } catch (Exception e) {
            Notifications.error(localization.i18n(e));
        }
    }

    private void confirmDelete(IdentityWorkspace workspace) {
        Dialogs.confirmDelete(
                localization,
                "Delete workspace",
                "Delete workspace confirmation",
                () -> delete(workspace)
        );
    }

    private void delete(IdentityWorkspace workspace) {
        try {
            workspaceService.delete(workspace.getUniqueId());
            Notifications.success(localization.i18n("Workspace deleted"));
            UI.getCurrent().navigate(IdentityWorkspaces.class);
        } catch (Exception e) {
            Notifications.error(localization.i18n(e));
        }
    }

    private IdentityWorkspace loadWorkspace(String workspaceId) {
        return workspaceService.getById(UniqueId.parse(workspaceId));
    }

    static RouteParameters routeParameters(IdentityWorkspace workspace) {
        return new RouteParameters(
                new RouteParam(WORKSPACE_ID_PARAMETER, workspace.getUniqueId().toString())
        );
    }

    private IdentityWorkspace copy(IdentityWorkspace workspace) {
        return IdentityWorkspace.builder()
                .uniqueId(workspace.getUniqueId())
                .version(workspace.getVersion())
                .createdAt(workspace.getCreatedAt())
                .updatedAt(workspace.getUpdatedAt())
                .name(workspace.getName())
                .description(workspace.getDescription())
                .build();
    }
}
