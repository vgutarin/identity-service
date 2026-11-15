package vg.identity.frontend.vaadin.admin;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.router.RouterLink;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.security.access.AccessDeniedException;
import vg.identity.frontend.vaadin.MainView;
import vg.identity.frontend.vaadin.Role;
import vg.identity.frontend.vaadin.service.LocalizationService;
import vg.identity.frontend.vaadin.ui.Notifications;
import vg.identity.model.IdentityApplication;
import vg.identity.model.IdentityApplicationMessage;
import vg.identity.model.IdentityApplicationUser;
import vg.identity.model.IdentityMessageFailureCode;
import vg.identity.model.IdentityMessageStatus;
import vg.identity.model.access.Permission;
import vg.identity.service.AuthorityChecker;
import vg.identity.service.IdentityApplicationMessageService;
import vg.identity.service.IdentityApplicationService;
import vg.identity.service.IdentityApplicationUserService;
import vg.unique.id.model.UniqueId;

import java.util.List;
import java.util.Map;

@Route(value = "admin/applications/:applicationId", layout = MainView.class)
@RolesAllowed(Role.OWNER)
public class IdentityApplicationDetails extends VerticalLayout implements BeforeEnterObserver, HasDynamicTitle {
    private static final int PAGE_SIZE = 50;
    private static final String MEMBERS = "members";
    private static final String MESSAGES = "messages";
    private final transient IdentityApplicationService applications;
    private final transient IdentityApplicationUserService members;
    private final transient IdentityApplicationMessageService messages;
    private final transient AuthorityChecker authorityChecker;
    private final LocalizationService localization;
    private final RouterLink back = new RouterLink();
    private final H2 name = new H2();
    private final Span applicationId = new Span();
    private final Span uri = new Span();
    private final Span created = new Span();
    private final Span updated = new Span();
    private final Span error = new Span();
    private final FormLayout details = new FormLayout();
    private final Tabs tabs = new Tabs();
    private final Tab membersTab;
    private final Tab messagesTab;
    private final Grid<IdentityApplicationUser> membersGrid = new Grid<>(IdentityApplicationUser.class, false);
    private Grid.Column<IdentityApplicationUser> sendMessageColumn;
    private final Grid<IdentityApplicationMessage> messagesGrid = new Grid<>(IdentityApplicationMessage.class, false);
    private final Button refresh;
    private final Button previous;
    private final Button next;
    private final Span pageLabel = new Span();
    private final HorizontalLayout paging = new HorizontalLayout();
    private IdentityApplication application;
    private Location currentLocation;
    private int page;
    private String selectedTab = MEMBERS;
    private boolean changingTabs;

    public IdentityApplicationDetails(IdentityApplicationService applications, IdentityApplicationUserService members,
                                      IdentityApplicationMessageService messages, AuthorityChecker authorityChecker,
                                      LocalizationService localization) {
        this.applications = applications;
        this.members = members;
        this.messages = messages;
        this.authorityChecker = authorityChecker;
        this.localization = localization;
        membersTab = new Tab(text("members"));
        messagesTab = new Tab(text("messages"));
        refresh = new Button(text("refresh"), VaadinIcon.REFRESH.create(), event -> refreshSelected());
        previous = new Button(text("previous"), event -> changePage(-1));
        next = new Button(text("next"), event -> changePage(1));
        configure();
    }

    private void configure() {
        setSizeFull();
        back.setText(text("back"));
        back.addClassName("navigation-back-button");
        var toolbar = new HorizontalLayout(back, refresh);
        toolbar.setWidthFull();
        toolbar.setAlignItems(FlexComponent.Alignment.CENTER);
        toolbar.expand(back);
        refresh.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        details.setWidthFull();
        details.addFormItem(applicationId, text("id"));
        details.addFormItem(uri, text("uri"));
        details.addFormItem(created, text("created"));
        details.addFormItem(updated, text("updated"));
        error.getElement().setAttribute("role", "alert");
        tabs.setWidthFull();
        tabs.addSelectedChangeListener(event -> {
            if (!changingTabs) {
                selectedTab = tabs.getSelectedTab() == messagesTab ? MESSAGES : MEMBERS;
                refreshSelected();
                updateTabUrl();
            }
        });
        configureMembers();
        configureMessages();
        paging.add(previous, pageLabel, next);
        paging.setAlignItems(FlexComponent.Alignment.CENTER);
        add(toolbar, name, details, error, tabs, membersGrid, messagesGrid, paging);
        expand(membersGrid, messagesGrid);
        clearView();
    }

    private void configureMembers() {
        membersGrid.setId("application-members-grid");
        membersGrid.setSizeFull();
        membersGrid.setEmptyStateText(text("members.empty"));
        membersGrid.addColumn(IdentityApplicationUser::displayName).setHeader(text("member.name")).setAutoWidth(true);
        membersGrid.addColumn(IdentityApplicationUser::uniqueId).setHeader(text("recipient")).setAutoWidth(true);
        membersGrid.addColumn(user -> localization.formatDateTime(user.firstAuthenticatedAt()))
                .setHeader(text("member.first")).setAutoWidth(true);
        membersGrid.addColumn(user -> localization.formatDateTime(user.lastAuthenticatedAt()))
                .setHeader(text("member.last")).setAutoWidth(true);
        sendMessageColumn = membersGrid.addComponentColumn(user -> {
            var button = new Button(VaadinIcon.ENVELOPE.create(), event -> openMessageDialog(user));
            button.setAriaLabel(text("send.action"));
            button.setTooltipText(text("send.action"));
            button.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            button.setVisible(sendMessageColumn.isVisible());
            return button;
        }).setKey("actions").setHeader(localization.i18n("Actions")).setAutoWidth(true).setFlexGrow(0);
        sendMessageColumn.setVisible(false);
    }

    private void configureMessages() {
        messagesGrid.setId("application-messages-grid");
        messagesGrid.setSizeFull();
        messagesGrid.setEmptyStateText(text("messages.empty"));
        messagesGrid.addColumn(IdentityApplicationMessage::messageUniqueId).setHeader(text("message.id")).setAutoWidth(true);
        messagesGrid.addColumn(IdentityApplicationMessage::userUniqueId).setHeader(text("recipient")).setAutoWidth(true);
        messagesGrid.addColumn(message -> statusText(message.status())).setHeader(text("message.status")).setAutoWidth(true);
        messagesGrid.addColumn(message -> localization.formatDateTime(message.createdAt())).setHeader(text("created")).setAutoWidth(true);
        messagesGrid.addColumn(message -> localization.formatDateTime(message.updatedAt())).setHeader(text("updated")).setAutoWidth(true);
        messagesGrid.addColumn(message -> failureText(message.failureCode())).setHeader(text("message.failure")).setAutoWidth(true);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        clearView();
        currentLocation = event.getLocation();
        selectedTab = currentLocation.getQueryParameters().getSingleParameter("tab")
                .filter(MESSAGES::equals).orElse(MEMBERS);
        try {
            var id = UniqueId.parse(event.getRouteParameters().get("applicationId").orElseThrow());
            application = applications.getById(id);
            if (application == null) {
                showUnavailable();
                return;
            }
            back.setRoute(IdentityWorkspaceDetails.class,
                    new RouteParameters("workspaceId", new UniqueId(application.getWorkspaceUniqueId()).toString()));
            back.setQueryParameters(QueryParameters.simple(Map.of("tab", "applications")));
            name.setText(application.getName());
            applicationId.setText(application.getUniqueId().toString());
            uri.setText(application.getUri() == null ? "" : application.getUri());
            created.setText(localization.formatDateTime(application.getCreatedAt()));
            updated.setText(localization.formatDateTime(application.getUpdatedAt()));
            name.setVisible(true);
            details.setVisible(true);
            refreshSelected();
        } catch (Exception ignored) {
            showUnavailable();
        }
    }

    private void clearView() {
        application = null;
        currentLocation = null;
        page = 0;
        back.setText(text("back"));
        back.setQueryParameters(QueryParameters.empty());
        back.setRoute(IdentityWorkspaces.class);
        name.setText("");
        name.setVisible(false);
        applicationId.setText("");
        uri.setText("");
        created.setText("");
        updated.setText("");
        details.setVisible(false);
        error.setVisible(false);
        tabs.setVisible(false);
        refresh.setVisible(false);
        clearGrids();
    }

    private void showUnavailable() {
        clearView();
        back.setText(text("workspaces"));
        showError("unavailable");
    }

    private void clearGrids() {
        sendMessageColumn.setVisible(false);
        membersGrid.setItems(List.of());
        messagesGrid.setItems(List.of());
        membersGrid.setVisible(false);
        messagesGrid.setVisible(false);
        paging.setVisible(false);
        previous.setEnabled(false);
        next.setEnabled(false);
    }

    private void refreshSelected() {
        clearGrids();
        error.setVisible(false);
        if (application == null) {
            return;
        }
        try {
            var canReadMembers = authorityChecker.hasAuthority(application.getUniqueId(), Permission.App.READ);
            var canReadMessages = authorityChecker.hasAuthority(application.getUniqueId(), Permission.App.MESSAGE_READ);
            sendMessageColumn.setVisible(canReadMembers
                    && authorityChecker.hasAuthority(application.getUniqueId(), Permission.App.MESSAGE_SEND));
            changingTabs = true;
            try {
                updatePermittedTabs(canReadMembers, canReadMessages);
                if (MESSAGES.equals(selectedTab) && !canReadMessages) {
                    selectedTab = MEMBERS;
                } else if (MEMBERS.equals(selectedTab) && !canReadMembers) {
                    selectedTab = MESSAGES;
                }
                tabs.setVisible(canReadMembers || canReadMessages);
                refresh.setVisible(canReadMembers || canReadMessages);
                if (!canReadMembers && !canReadMessages) {
                    showError("noAccess");
                    return;
                }
                tabs.setSelectedTab(MESSAGES.equals(selectedTab) ? messagesTab : membersTab);
            } finally {
                changingTabs = false;
            }
            if (MESSAGES.equals(selectedTab)) {
                loadMessages();
            } else {
                membersGrid.setItems(members.findUsers(application.getUniqueId()));
                membersGrid.setVisible(true);
            }
        } catch (AccessDeniedException ignored) {
            clearGrids();
            tabs.setVisible(false);
            refresh.setVisible(false);
            showError("noAccess");
        } catch (Exception ignored) {
            clearGrids();
            showError("loadError");
        }
    }

    private void updatePermittedTabs(boolean canReadMembers, boolean canReadMessages) {
        if (!canReadMembers && membersTab.getParent().isPresent()) {
            tabs.remove(membersTab);
        }
        if (!canReadMessages && messagesTab.getParent().isPresent()) {
            tabs.remove(messagesTab);
        }
        if (canReadMembers && membersTab.getParent().isEmpty()) {
            tabs.addTabAtIndex(0, membersTab);
        }
        if (canReadMessages && messagesTab.getParent().isEmpty()) {
            tabs.add(messagesTab);
        }
    }

    private void openMessageDialog(IdentityApplicationUser user) {
        if (application == null) {
            return;
        }
        if (!authorityChecker.hasAuthority(application.getUniqueId(), Permission.App.MESSAGE_SEND)) {
            sendMessageColumn.setVisible(false);
            Notifications.error(text("send.denied"));
            return;
        }
        new IdentityApplicationMessageDialog(application.getUniqueId(), user, messages, localization).open();
    }

    private void loadMessages() {
        var result = messages.findMessages(application.getUniqueId(), page, PAGE_SIZE);
        if (page > 0 && page >= result.getTotalPages()) {
            page = Math.max(0, result.getTotalPages() - 1);
            result = messages.findMessages(application.getUniqueId(), page, PAGE_SIZE);
        }
        messagesGrid.setItems(result.getContent());
        messagesGrid.setVisible(true);
        previous.setEnabled(result.hasPrevious());
        next.setEnabled(result.hasNext());
        pageLabel.setText(localization.i18n("application.details.page", result.getTotalPages() == 0 ? 0 : page + 1,
                result.getTotalPages(), result.getTotalElements()));
        paging.setVisible(true);
    }

    private void changePage(int delta) {
        page = Math.max(0, page + delta);
        refreshSelected();
    }

    private void showError(String key) {
        error.setText(text(key));
        error.setVisible(true);
    }

    private void updateTabUrl() {
        if (application != null) {
            var query = currentLocation.getQueryParameters().excluding("tab").merging("tab", selectedTab);
            currentLocation = new Location(currentLocation.getPath(), query);
            UI.getCurrent().getPage().getHistory().replaceState(null, currentLocation);
        }
    }

    String statusText(IdentityMessageStatus status) {
        return status == null ? text("unknown") : text("status." + status.name());
    }

    String failureText(IdentityMessageFailureCode failure) {
        return failure == null ? text("failure.none") : text("failure." + failure.name());
    }

    private String text(String key) {
        var translationKey = "application.details." + key;
        var translated = localization.i18n(translationKey);
        return translationKey.equals(translated) ? localization.i18n("application.details.unknown") : translated;
    }

    @Override
    public String getPageTitle() {
        return text("title");
    }

    static RouteParameters routeParameters(IdentityApplication application) {
        return new RouteParameters("applicationId", application.getUniqueId().toString());
    }
}
