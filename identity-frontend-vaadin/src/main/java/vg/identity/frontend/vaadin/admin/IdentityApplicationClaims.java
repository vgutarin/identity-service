package vg.identity.frontend.vaadin.admin;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import vg.identity.frontend.vaadin.MainView;
import vg.identity.frontend.vaadin.Role;
import vg.identity.frontend.vaadin.service.LocalizationService;
import vg.identity.frontend.vaadin.ui.Dialogs;
import vg.identity.frontend.vaadin.ui.Notifications;
import vg.identity.model.IdentityApplication;
import vg.identity.model.IdentityApplicationUser;
import vg.identity.model.IdentityApplicationUserPrincipal;
import vg.identity.model.IdentityWorkspace;
import vg.identity.service.IdentityApplicationClaimService;
import vg.identity.service.IdentityApplicationService;
import vg.identity.service.IdentityApplicationUserService;
import vg.identity.service.IdentityWorkspaceService;
import vg.unique.id.model.UniqueId;

import java.util.List;
import java.util.Map;

/**
 * Workspace-administrator view for managing an application's per-user claims ({@code scope -> claims}). Pick a
 * workspace and one of its applications, then grant or revoke direct claims for each user that has authenticated
 * for that application. These are application-local claims, independent of the platform's own permissions.
 */
@PageTitle("Application claims")
@Route(value = "admin/application-claims", layout = MainView.class)
@RolesAllowed(Role.OWNER)
public class IdentityApplicationClaims extends VerticalLayout {

    private final IdentityApplicationService applicationService;
    private final IdentityApplicationUserService applicationUserService;
    private final IdentityApplicationClaimService claimService;
    private final LocalizationService localization;

    private final ComboBox<IdentityWorkspace> workspaceSelect = new ComboBox<>();
    private final ComboBox<IdentityApplication> applicationSelect = new ComboBox<>();
    private final Grid<IdentityApplicationUser> usersGrid = new Grid<>(IdentityApplicationUser.class, false);

    public IdentityApplicationClaims(
            IdentityWorkspaceService workspaceService,
            IdentityApplicationService applicationService,
            IdentityApplicationUserService applicationUserService,
            IdentityApplicationClaimService claimService,
            LocalizationService localization
    ) {
        this.applicationService = applicationService;
        this.applicationUserService = applicationUserService;
        this.claimService = claimService;
        this.localization = localization;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        configureSelectors();
        configureUsersGrid();

        var selectors = new HorizontalLayout(workspaceSelect, applicationSelect);
        selectors.setAlignItems(FlexComponent.Alignment.END);
        add(selectors, usersGrid);
        expand(usersGrid);

        workspaceSelect.setItems(workspaceService.getAll());
    }

    private void configureSelectors() {
        workspaceSelect.setLabel(localization.i18n("Workspace"));
        workspaceSelect.setItemLabelGenerator(IdentityWorkspace::getName);
        workspaceSelect.setWidth("320px");
        workspaceSelect.addValueChangeListener(event -> {
            applicationSelect.clear();
            var workspace = event.getValue();
            applicationSelect.setEnabled(workspace != null);
            applicationSelect.setItems(workspace == null
                    ? List.of()
                    : applicationService.findByWorkspaceUniqueId(workspace.getUniqueId()));
            refreshUsers();
        });

        applicationSelect.setLabel(localization.i18n("Application"));
        applicationSelect.setItemLabelGenerator(IdentityApplication::getName);
        applicationSelect.setWidth("320px");
        applicationSelect.setEnabled(false);
        applicationSelect.addValueChangeListener(event -> refreshUsers());
    }

    private void configureUsersGrid() {
        usersGrid.setSizeFull();
        usersGrid.setEmptyStateText(localization.i18n("No application users found"));
        usersGrid.addColumn(user -> user.uniqueId().toString())
                .setHeader(localization.i18n("User"))
                .setAutoWidth(true);
        usersGrid.addColumn(user -> localization.formatDateTime(user.lastAuthenticatedAt()))
                .setHeader(localization.i18n("Last authenticated"))
                .setSortable(true)
                .setComparator(IdentityApplicationUser::lastAuthenticatedAt)
                .setAutoWidth(true);
        usersGrid.addComponentColumn(user -> {
            var manage = new Button(localization.i18n("Manage claims"), VaadinIcon.KEY.create());
            manage.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            manage.addClickListener(event -> openClaimsDialog(user));
            return manage;
        }).setHeader(localization.i18n("Actions")).setAutoWidth(true).setFlexGrow(0);
    }

    private void refreshUsers() {
        var application = applicationSelect.getValue();
        usersGrid.setItems(application == null
                ? List.of()
                : applicationUserService.findUsers(application.getUniqueId()));
    }

    private void openClaimsDialog(IdentityApplicationUser user) {
        var application = applicationSelect.getValue();
        if (application == null) {
            return;
        }
        var applicationUniqueId = application.getUniqueId();
        var userUniqueId = user.uniqueId();

        var dialog = Dialogs.form(localization.i18n("Claims") + ": " + userUniqueId, 760);

        var claimsGrid = new Grid<ClaimRow>();
        claimsGrid.setWidthFull();
        claimsGrid.setHeight("360px");
        claimsGrid.setEmptyStateText(localization.i18n("No claims granted"));
        claimsGrid.addColumn(ClaimRow::scope).setHeader(localization.i18n("Scope")).setAutoWidth(true);
        claimsGrid.addColumn(ClaimRow::claim).setHeader(localization.i18n("Claim")).setAutoWidth(true).setFlexGrow(1);
        claimsGrid.addComponentColumn(row -> revokeButton(claimsGrid, applicationUniqueId, userUniqueId, row))
                .setHeader(localization.i18n("Actions")).setAutoWidth(true).setFlexGrow(0);

        var scopeField = new TextField(localization.i18n("Scope"));
        scopeField.setValue(IdentityApplicationUserPrincipal.PERMISSIONS_SCOPE);
        var claimField = new TextField(localization.i18n("Claim"));
        var grant = new Button(localization.i18n("Grant"), VaadinIcon.PLUS.create());
        grant.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        grant.addClickListener(event -> grantClaim(claimsGrid, applicationUniqueId, userUniqueId, scopeField, claimField));

        var addBar = new HorizontalLayout(scopeField, claimField, grant);
        addBar.setWidthFull();
        addBar.setAlignItems(FlexComponent.Alignment.END);

        var close = new Button(localization.i18n("Close"), event -> dialog.close());
        dialog.add(addBar, claimsGrid);
        dialog.getFooter().add(close);
        refreshClaims(claimsGrid, applicationUniqueId, userUniqueId);
        dialog.open();
    }

    private Button revokeButton(Grid<ClaimRow> claimsGrid, UniqueId applicationUniqueId, UniqueId userUniqueId, ClaimRow row) {
        var revoke = new Button(VaadinIcon.TRASH.create());
        revoke.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        revoke.addClickListener(event -> {
            try {
                claimService.revokeClaim(applicationUniqueId, userUniqueId, row.scope(), row.claim());
                Notifications.success(localization.i18n("Claim removed"));
                refreshClaims(claimsGrid, applicationUniqueId, userUniqueId);
            } catch (RuntimeException e) {
                Notifications.error(localization.i18n(e));
            }
        });
        return revoke;
    }

    private void grantClaim(
            Grid<ClaimRow> claimsGrid,
            UniqueId applicationUniqueId,
            UniqueId userUniqueId,
            TextField scopeField,
            TextField claimField
    ) {
        if (isBlank(scopeField.getValue()) || isBlank(claimField.getValue())) {
            Notifications.error(localization.i18n("Scope and claim are required"));
            return;
        }
        try {
            claimService.grantClaim(applicationUniqueId, userUniqueId, scopeField.getValue(), claimField.getValue());
            claimField.clear();
            Notifications.success(localization.i18n("Claim saved"));
            refreshClaims(claimsGrid, applicationUniqueId, userUniqueId);
        } catch (RuntimeException e) {
            Notifications.error(localization.i18n(e));
        }
    }

    private void refreshClaims(Grid<ClaimRow> claimsGrid, UniqueId applicationUniqueId, UniqueId userUniqueId) {
        var claimsByScope = claimService.getUserClaims(applicationUniqueId, userUniqueId);
        claimsGrid.setItems(toRows(claimsByScope));
    }

    private static List<ClaimRow> toRows(Map<String, java.util.Set<String>> claimsByScope) {
        return claimsByScope.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().map(claim -> new ClaimRow(entry.getKey(), claim)))
                .sorted((a, b) -> {
                    var byScope = a.scope().compareTo(b.scope());
                    return byScope != 0 ? byScope : a.claim().compareTo(b.claim());
                })
                .toList();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ClaimRow(String scope, String claim) {
    }
}
