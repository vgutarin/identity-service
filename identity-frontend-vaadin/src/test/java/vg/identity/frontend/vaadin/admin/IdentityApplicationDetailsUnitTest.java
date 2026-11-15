package vg.identity.frontend.vaadin.admin;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.router.RouterLink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import vg.identity.frontend.vaadin.service.LocalizationService;
import vg.identity.model.IdentityApplication;
import vg.identity.model.IdentityApplicationMessage;
import vg.identity.model.IdentityApplicationUser;
import vg.identity.model.IdentityMessageStatus;
import vg.identity.model.access.Permission;
import vg.identity.service.AuthorityChecker;
import vg.identity.service.IdentityApplicationMessageService;
import vg.identity.service.IdentityApplicationService;
import vg.identity.service.IdentityApplicationUserService;
import vg.unique.id.model.UniqueId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ViewPackages(classes = IdentityApplicationDetails.class)
class IdentityApplicationDetailsUnitTest extends BrowserlessTest {
    private static final UniqueId ID = new UniqueId(42L);
    @Mock private IdentityApplicationService applications;
    @Mock private IdentityApplicationUserService members;
    @Mock private IdentityApplicationMessageService messages;
    @Mock private AuthorityChecker permissions;
    @Mock private LocalizationService localization;
    @Mock private BeforeEnterEvent event;
    private IdentityApplicationDetails view;

    @BeforeEach
    void setUp() {
        lenient().when(localization.i18n(anyString())).thenAnswer(call -> call.getArgument(0) + " translated");
        lenient().when(localization.i18n(anyString(), any(), any(), any())).thenReturn("page summary");
        view = new IdentityApplicationDetails(applications, members, messages, permissions, localization);
        UI.getCurrent().add(view);
    }

    @Test
    void beforeEnter_whenMembersIsDefault_loadsOnlyMembersAndCreatesWorkspaceBackLink() {
        prepare(null, true, true);
        when(members.findUsers(ID)).thenReturn(List.of(member()));

        view.beforeEnter(event);

        assertThat(tabs().getSelectedTab().getLabel()).contains("members");
        assertThat(test(grid("application-members-grid")).size()).isEqualTo(1);
        assertThat(back().getHref()).isEqualTo("admin/workspaces/" + new UniqueId(7L) + "?tab=applications");
        verifyNoInteractions(messages);
    }

    @Test
    void beforeEnter_whenSendIsNotGranted_hidesMemberActionsDespiteReadPermissions() {
        prepare(null, true, true);
        when(members.findUsers(ID)).thenReturn(List.of(member()));

        view.beforeEnter(event);

        assertThat(grid("application-members-grid").getColumnByKey("actions").isVisible()).isFalse();
        verifyNoInteractions(messages);
    }

    @Test
    void refresh_whenSendPermissionChanges_updatesMemberActionsVisibility() {
        prepare(null, true, false);
        when(members.findUsers(ID)).thenReturn(List.of(member()));
        view.beforeEnter(event);
        var column = grid("application-members-grid").getColumnByKey("actions");
        when(permissions.hasAuthority(ID, Permission.App.MESSAGE_SEND)).thenReturn(true);

        test(button("refresh")).click();

        assertThat(column.isVisible()).isTrue();
        var send = (Button) test(grid("application-members-grid")).getCellComponent(0, "actions");
        assertThat(send.isVisible()).isTrue();
        assertThat(send.getText()).isEmpty();
        assertThat(send.getIcon()).isNotNull();
        assertThat(send.getAriaLabel()).contains("application.details.send.action translated");
        when(permissions.hasAuthority(ID, Permission.App.MESSAGE_SEND)).thenReturn(false);

        test(button("refresh")).click();

        assertThat(column.isVisible()).isFalse();
        verifyNoInteractions(messages);
    }

    @Test
    void openMessageDialog_whenSendPermissionWasRevoked_doesNotOpenOrSubmit() {
        prepare(null, true, false);
        when(members.findUsers(ID)).thenReturn(List.of(member()));
        when(permissions.hasAuthority(ID, Permission.App.MESSAGE_SEND)).thenReturn(true);
        view.beforeEnter(event);
        var send = (Button) test(grid("application-members-grid")).getCellComponent(0, "actions");
        when(permissions.hasAuthority(ID, Permission.App.MESSAGE_SEND)).thenReturn(false);

        test(send).click();

        assertThat(find(Dialog.class).all()).isEmpty();
        assertThat(grid("application-members-grid").getColumnByKey("actions").isVisible()).isFalse();
        verifyNoInteractions(messages);
    }

    @Test
    void beforeEnter_whenRequestedTabIsDenied_hidesItAndNeverLoadsItsData() {
        prepare("messages", true, false);
        when(members.findUsers(ID)).thenReturn(List.of());

        view.beforeEnter(event);

        assertThat(tabs().getChildren().filter(Tab.class::isInstance)).hasSize(1);
        assertThat(tabs().getSelectedTab().getLabel()).contains("members");
        verifyNoInteractions(messages);
    }

    @Test
    void beforeEnter_whenOnlyMessageReadIsGranted_fallsBackToMessagesWithoutLoadingMembers() {
        prepare("nonsense", false, true);
        when(messages.findMessages(ID, 0, 50)).thenReturn(new PageImpl<>(List.of()));

        view.beforeEnter(event);

        assertThat(tabs().getSelectedTab().getLabel()).contains("messages");
        assertThat(grid("application-messages-grid").isVisible()).isTrue();
        verifyNoInteractions(members);
    }

    @Test
    void refresh_whenPermissionIsRevoked_hidesTabAndClearsItsCachedRows() {
        prepare("messages", false, true);
        when(messages.findMessages(ID, 0, 50)).thenReturn(new PageImpl<>(List.of(message())));
        view.beforeEnter(event);
        assertThat(test(grid("application-messages-grid")).size()).isEqualTo(1);
        when(permissions.hasAuthority(ID, Permission.App.MESSAGE_READ)).thenReturn(false);

        test(button("refresh")).click();

        assertThat(tabs().isVisible()).isFalse();
        assertThat(grid("application-messages-grid").isVisible()).isFalse();
        assertThat(grid("application-messages-grid").getListDataView().getItemCount()).isZero();
        assertThat(errorText()).contains("noAccess");
    }

    @Test
    void paging_whenNextAndPreviousAreClicked_requestsBoundedPages() {
        prepare("messages", true, true);
        var requestedPages = new ArrayList<Integer>();
        when(messages.findMessages(eq(ID), anyInt(), eq(50))).thenAnswer(call -> {
            int page = call.getArgument(1);
            requestedPages.add(page);
            return new PageImpl<>(List.of(message()), PageRequest.of(page, 50), 101);
        });
        view.beforeEnter(event);
        assertThat(button("previous").isEnabled()).isFalse();

        test(button("next")).click();
        test(button("previous")).click();

        assertThat(requestedPages).containsExactly(0, 1, 0);
        assertThat(button("previous").isEnabled()).isFalse();
        verifyNoInteractions(members);
    }

    @Test
    void refresh_whenCurrentPageWasRemoved_returnsToLastAvailablePage() {
        prepare("messages", true, true);
        when(messages.findMessages(ID, 0, 50)).thenReturn(new PageImpl<>(List.of(message()), PageRequest.of(0, 50), 51));
        when(messages.findMessages(ID, 1, 50)).thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 50), 0));
        view.beforeEnter(event);

        test(button("next")).click();

        assertThat(button("previous").isEnabled()).isFalse();
        assertThat(grid("application-messages-grid").getListDataView().getItemCount()).isEqualTo(1);
    }

    @Test
    void beforeEnter_whenServiceFails_showsSanitizedErrorInsteadOfEmptyResults() {
        prepare("messages", true, true);
        when(messages.findMessages(ID, 0, 50)).thenThrow(new IllegalStateException("private-token-and-text"));

        view.beforeEnter(event);

        assertThat(errorText()).contains("loadError").doesNotContain("private-token-and-text");
        assertThat(grid("application-messages-grid").isVisible()).isFalse();
    }

    @Test
    void beforeEnter_whenMemberServiceDeniesAccess_clearsDataAndShowsPermissionError() {
        prepare(null, true, true);
        when(members.findUsers(ID)).thenThrow(new AccessDeniedException("private details"));

        view.beforeEnter(event);

        assertThat(errorText()).contains("noAccess").doesNotContain("private details");
        assertThat(grid("application-members-grid").isVisible()).isFalse();
        verifyNoInteractions(messages);
    }

    @Test
    void refresh_whenPermissionsAreUnchanged_keepsTabsAttachedAndSelectionStableDuringPaging() {
        prepare("messages", true, true);
        when(messages.findMessages(eq(ID), anyInt(), eq(50))).thenAnswer(call ->
                new PageImpl<>(List.of(message()), PageRequest.of(call.getArgument(1), 50), 101));
        view.beforeEnter(event);
        var originalTabs = tabs().getChildren().map(Tab.class::cast).toList();
        var detached = new AtomicInteger();
        var selectionChanges = new AtomicInteger();
        originalTabs.forEach(tab -> tab.addDetachListener(detach -> detached.incrementAndGet()));
        tabs().addSelectedChangeListener(change -> selectionChanges.incrementAndGet());

        test(button("refresh")).click();
        test(button("next")).click();
        test(button("previous")).click();

        assertThat(tabs().getChildren()).containsExactlyElementsOf(originalTabs);
        assertThat(tabs().getSelectedTab()).isSameAs(originalTabs.get(1));
        assertThat(detached).hasValue(0);
        assertThat(selectionChanges).hasValue(0);
    }

    @Test
    void refresh_whenPermissionsChange_updatesOnlyAffectedTabsAndRetainsTheirOrder() {
        prepare("messages", false, true);
        var messageLoads = new AtomicInteger();
        when(messages.findMessages(ID, 0, 50)).thenAnswer(call -> {
            messageLoads.incrementAndGet();
            return new PageImpl<>(List.of(message()));
        });
        view.beforeEnter(event);
        var messageTab = tabs().getSelectedTab();
        var messageDetached = new AtomicInteger();
        messageTab.addDetachListener(detach -> messageDetached.incrementAndGet());
        when(permissions.hasAuthority(ID, Permission.App.READ)).thenReturn(true);

        test(button("refresh")).click();

        assertThat(tabs().getTabCount()).isEqualTo(2);
        assertThat(tabs().getTabAt(1)).isSameAs(messageTab);
        assertThat(tabs().getSelectedTab()).isSameAs(messageTab);
        assertThat(messageDetached).hasValue(0);
        var memberTab = tabs().getTabAt(0);
        var memberDetached = new AtomicInteger();
        memberTab.addDetachListener(detach -> memberDetached.incrementAndGet());
        when(permissions.hasAuthority(ID, Permission.App.MESSAGE_READ)).thenReturn(false);
        when(members.findUsers(ID)).thenReturn(List.of(member()));

        test(button("refresh")).click();

        assertThat(tabs().getChildren()).containsExactly(memberTab);
        assertThat(tabs().getSelectedTab()).isSameAs(memberTab);
        assertThat(messageTab.getParent()).isEmpty();
        assertThat(messageDetached).hasValue(1);
        assertThat(memberDetached).hasValue(0);
        assertThat(messageLoads).hasValue(2);
    }

    @Test
    void tabSelection_whenQueryHasOtherParameters_replacesHistoryWithoutLosingRouteOrValues() {
        prepare("members&filter=a%20b&tag=one&tag=two", true, true);
        when(members.findUsers(ID)).thenReturn(List.of(member()));
        when(messages.findMessages(ID, 0, 50)).thenReturn(new PageImpl<>(List.of(message())));
        view.beforeEnter(event);
        UI.getCurrent().getInternals().dumpPendingJavaScriptInvocations();

        tabs().setSelectedTab(tabs().getTabAt(1));

        var messageLocation = historyLocation();
        assertThat(messageLocation.getPath()).isEqualTo("admin/applications/" + ID);
        assertThat(messageLocation.getQueryParameters().getParameters()).isEqualTo(Map.of(
                "tab", List.of("messages"), "filter", List.of("a b"), "tag", List.of("one", "two")));

        tabs().setSelectedTab(tabs().getTabAt(0));

        assertThat(historyLocation().getQueryParameters().getParameters()).isEqualTo(Map.of(
                "tab", List.of("members"), "filter", List.of("a b"), "tag", List.of("one", "two")));
    }

    @Test
    void statusText_whenTranslationIsMissing_usesLocalizedUnknownInsteadOfRawEnumOrKey() {
        when(localization.i18n("application.details.status.SENT")).thenReturn("application.details.status.SENT");

        assertThat(view.statusText(IdentityMessageStatus.SENT)).isEqualTo("application.details.unknown translated");
    }

    @Test
    void beforeEnter_whenApplicationIsMissing_showsUnavailableWithoutLoadingTabs() {
        when(event.getRouteParameters()).thenReturn(new RouteParameters("applicationId", ID.toString()));
        when(event.getLocation()).thenReturn(new Location("admin/applications/" + ID));
        when(applications.getById(ID)).thenReturn(null);

        view.beforeEnter(event);

        assertThat(errorText()).contains("unavailable");
        assertThat(back().getHref()).isEqualTo("admin/workspaces");
        verifyNoInteractions(members, messages, permissions);
    }

    @Test
    void beforeEnter_whenIdentifierIsInvalid_showsUnavailableWithoutQueryingServices() {
        when(event.getRouteParameters()).thenReturn(new RouteParameters("applicationId", "%%%"));
        when(event.getLocation()).thenReturn(new Location("admin/applications/invalid"));

        view.beforeEnter(event);

        assertThat(errorText()).contains("unavailable");
        verifyNoInteractions(applications, members, messages, permissions);
    }

    private void prepare(String tab, boolean memberRead, boolean messageRead) {
        when(event.getRouteParameters()).thenReturn(new RouteParameters("applicationId", ID.toString()));
        when(event.getLocation()).thenReturn(new Location("admin/applications/" + ID + (tab == null ? "" : "?tab=" + tab)));
        when(applications.getById(ID)).thenReturn(IdentityApplication.builder().uniqueId(ID).workspaceUniqueId(7L)
                .name("Example").uri("https://t.me/example").payload("private-token-and-text").build());
        when(permissions.hasAuthority(ID, Permission.App.READ)).thenReturn(memberRead);
        when(permissions.hasAuthority(ID, Permission.App.MESSAGE_READ)).thenReturn(messageRead);
    }

    private Tabs tabs() {
        return view.getChildren().filter(Tabs.class::isInstance).map(Tabs.class::cast).findFirst().orElseThrow();
    }

    private Location historyLocation() {
        var paths = UI.getCurrent().getInternals().dumpPendingJavaScriptInvocations().stream()
                .flatMap(invocation -> invocation.getInvocation().getParameters().stream())
                .filter(String.class::isInstance).map(String.class::cast)
                .filter(path -> path.startsWith("admin/applications/" + ID)).toList();
        assertThat(paths).hasSize(1);
        return new Location(paths.getFirst());
    }

    private RouterLink back() {
        return find(RouterLink.class, view).single();
    }

    private Grid<?> grid(String id) {
        return view.getChildren().filter(Grid.class::isInstance).map(Grid.class::cast)
                .filter(grid -> grid.getId().orElse("").equals(id)).findFirst().orElseThrow();
    }

    private Button button(String key) {
        return find(Button.class, view).all().stream()
                .filter(button -> button.getText().equals("application.details." + key + " translated")).findFirst().orElseThrow();
    }

    private String errorText() {
        return find(Span.class, view).all().stream().filter(span -> "alert".equals(span.getElement().getAttribute("role")))
                .map(Span::getText).findFirst().orElseThrow();
    }

    private IdentityApplicationUser member() {
        return IdentityApplicationUser.builder().uniqueId(new UniqueId(2L)).displayName("A user")
                .firstAuthenticatedAt(Instant.EPOCH).lastAuthenticatedAt(Instant.EPOCH).build();
    }

    private IdentityApplicationMessage message() {
        return IdentityApplicationMessage.builder().messageUniqueId(new UniqueId(3L)).userUniqueId(new UniqueId(2L))
                .status(IdentityMessageStatus.SENT).build();
    }
}
