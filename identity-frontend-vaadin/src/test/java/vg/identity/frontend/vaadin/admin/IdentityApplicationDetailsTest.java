package vg.identity.frontend.vaadin.admin;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.browserless.SpringSecurityContextHandler;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vg.identity.frontend.vaadin.auth.LoginView;
import vg.identity.frontend.vaadin.service.LocalizationService;
import vg.identity.frontend.vaadin.service.TelegramWebhookUiTasks;
import vg.identity.frontend.vaadin.ui.LocalePicker;
import vg.identity.model.IdentityApplication;
import vg.identity.model.IdentityApplicationMessage;
import vg.identity.model.IdentityApplicationMessageRequest;
import vg.identity.model.IdentityApplicationUser;
import vg.identity.model.IdentityMessageFailureCode;
import vg.identity.model.IdentityMessageStatus;
import vg.identity.model.IdentityMessageSubmission;
import vg.identity.model.IdentityWorkspace;
import vg.identity.model.access.Permission;
import vg.identity.service.AuthorityChecker;
import vg.identity.service.IdentityApiKeyService;
import vg.identity.service.IdentityApplicationMessageService;
import vg.identity.service.IdentityApplicationService;
import vg.identity.service.IdentityApplicationUserService;
import vg.identity.service.IdentityWorkspaceService;
import vg.unique.id.model.UniqueId;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:fe-application-details-test;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("test")
class IdentityApplicationDetailsTest extends SpringBrowserlessTest {
    private static final UniqueId ID = new UniqueId(42L);
    @MockitoBean private IdentityApplicationService applications;
    @MockitoBean private IdentityApplicationUserService members;
    @MockitoBean private IdentityApplicationMessageService messages;
    @MockitoBean private AuthorityChecker permissions;
    @MockitoBean private IdentityWorkspaceService workspaces;
    @MockitoBean private IdentityApiKeyService apiKeys;
    @Autowired private LocalizationService localization;
    private IdentityApplication application;

    @BeforeEach
    void setUp() {
        authenticate("owner", "OWNER");
        localization.setCurrentLocale(Locale.ENGLISH);
        application = IdentityApplication.builder().uniqueId(ID).workspaceUniqueId(7L).name("Sample app")
                .uri("https://t.me/sample_app").payload("never-show-this-token").createdAt(Instant.EPOCH)
                .updatedAt(Instant.EPOCH).build();
        when(applications.getById(ID)).thenReturn(application);
        when(permissions.hasAuthority(eq(ID), anyString())).thenReturn(true);
        when(members.findUsers(ID)).thenReturn(List.of(IdentityApplicationUser.builder().uniqueId(new UniqueId(2L))
                .displayName("Alice").firstAuthenticatedAt(Instant.EPOCH).lastAuthenticatedAt(Instant.EPOCH).build()));
        when(messages.findMessages(ID, 0, 50)).thenReturn(new PageImpl<>(List.of(message())));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void navigate_whenOwnerOpensDetails_showsLocalizedShellAndSafeDetails() {
        var view = navigate("admin/applications/" + ID, IdentityApplicationDetails.class);

        assertThat(view.getPageTitle()).isEqualTo("Application details");
        assertThat(find(H3.class).all()).anyMatch(title -> title.getText().equals("Application details"));
        assertThat(view.getElement().getOuterHTML()).doesNotContain("never-show-this-token");
        assertThat(test(grid("application-members-grid")).getCellText(0, 0)).isEqualTo("Alice");
        verifyNoInteractions(messages);
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "uk-UA"})
    void sendMessage_whenSendIsGrantedWithoutHistoryRead_opensLocalizedDialogAndSubmitsToSelectedMember(String language) {
        localization.setCurrentLocale(Locale.forLanguageTag(language));
        when(permissions.hasAuthority(ID, Permission.App.MESSAGE_READ)).thenReturn(false);
        var submitted = new AtomicReference<IdentityApplicationMessageRequest>();
        when(messages.submitMessage(eq(ID), eq(new UniqueId(2L)), any())).thenAnswer(call -> {
            submitted.set(call.getArgument(2));
            return IdentityMessageSubmission.builder().message(message()).build();
        });
        navigate("admin/applications/" + ID, IdentityApplicationDetails.class);
        var grid = grid("application-members-grid");
        var action = (Button) test(grid).getCellComponent(0, "actions");
        assertThat(test(grid).getHeaderCell(4)).isEqualTo(localization.i18n("Actions"));
        assertThat(action.getText()).isEmpty();
        assertThat(action.getIcon()).isNotNull();
        assertThat(action.getAriaLabel()).contains(localization.i18n("application.details.send.action"));
        assertThat(action.getTooltip().getText()).isEqualTo(localization.i18n("application.details.send.action"));
        assertThat(find(Tabs.class).single().getTabCount()).isEqualTo(1);

        test(action).click();

        var dialog = find(Dialog.class).single();
        assertThat(dialog.getHeaderTitle()).isEqualTo(localization.i18n("application.details.send.action"));
        var field = find(TextArea.class).single();
        assertThat(field.getLabel()).isEqualTo(localization.i18n("application.details.send.text"));
        assertThat(find(Button.class, dialog).all()).extracting(Button::getText).contains(
                localization.i18n("application.details.send.cancel"), localization.i18n("application.details.send.submit"));
        field.setValue(" \n");
        test(dialogButton("submit")).click();
        assertThat(field.getErrorMessage()).isEqualTo(localization.i18n("application.details.send.validation"));
        assertThat(submitted.get()).isNull();
        field.setValue("Hello, Alice");

        test(dialogButton("submit")).click();

        assertThat(dialog.isOpened()).isFalse();
        assertThat(submitted.get().content().text()).isEqualTo("Hello, Alice");
        assertThat(submitted.get().idempotencyKey()).isNotNull();
    }

    @Test
    void navigate_whenOnlyReadPermissionsAreGranted_hidesMemberActions() {
        when(permissions.hasAuthority(ID, Permission.App.MESSAGE_SEND)).thenReturn(false);

        navigate("admin/applications/" + ID, IdentityApplicationDetails.class);

        assertThat(grid("application-members-grid").getColumnByKey("actions").isVisible()).isFalse();
        assertThat(find(Dialog.class).all()).isEmpty();
        verifyNoInteractions(messages);
    }

    @Test
    void sendMessage_whenServiceRejectsRevokedPermission_preservesTextAndShowsSanitizedDenial() {
        when(messages.submitMessage(eq(ID), eq(new UniqueId(2L)), any()))
                .thenThrow(new AccessDeniedException("private-token-and-text"));
        navigate("admin/applications/" + ID, IdentityApplicationDetails.class);
        test((Button) test(grid("application-members-grid")).getCellComponent(0, "actions")).click();
        find(TextArea.class).single().setValue("Keep this draft");

        test(dialogButton("submit")).click();

        var dialog = find(Dialog.class).single();
        assertThat(dialog.isOpened()).isTrue();
        assertThat(find(TextArea.class).single().getValue()).isEqualTo("Keep this draft");
        assertThat(find(Span.class, dialog).all()).filteredOn(span ->
                        "alert".equals(span.getElement().getAttribute("role")))
                .extracting(Span::getText).containsExactly(localization.i18n("application.details.send.denied"));
        assertThat(dialog.getElement().getOuterHTML()).doesNotContain("private-token-and-text");
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "uk-UA"})
    void navigate_whenLocaleIsSelected_translatesHeadersDatesStatusesAndFailures(String language) {
        localization.setCurrentLocale(Locale.forLanguageTag(language));
        var view = openMessages();
        var grid = grid("application-messages-grid");

        assertThat(view.getPageTitle()).isEqualTo(localization.i18n("application.details.title"));
        assertThat(test(grid).getHeaderCell(0)).isEqualTo(localization.i18n("application.details.message.id"));
        assertThat(test(grid).getCellText(0, 2)).isEqualTo(localization.i18n("application.details.status.UNKNOWN"));
        assertThat(test(grid).getCellText(0, 3)).isEqualTo(localization.formatDateTime(Instant.EPOCH));
        assertThat(test(grid).getCellText(0, 5)).isEqualTo(localization.i18n("application.details.failure.DELIVERY_UNKNOWN"));
        assertThat(find(Tab.class).all()).extracting(Tab::getLabel)
                .contains(localization.i18n("application.details.members"), localization.i18n("application.details.messages"));
        assertThat(find(Button.class).all()).extracting(Button::getText)
                .contains(localization.i18n("application.details.previous"), localization.i18n("application.details.next"));
        for (var status : IdentityMessageStatus.values()) {
            assertThat(view.statusText(status)).isNotEqualTo(status.name()).doesNotContain("application.details.");
        }
        for (var failure : IdentityMessageFailureCode.values()) {
            assertThat(view.failureText(failure)).isNotEqualTo(failure.name()).doesNotContain("application.details.");
        }
        assertThat(view.statusText(null)).isEqualTo(localization.i18n("application.details.unknown"));
        verifyNoInteractions(members);
    }

    @Test
    void tabSelection_whenHistoryChanges_keepsViewAndUnrelatedQueryParameters() {
        var loads = new AtomicInteger();
        when(applications.getById(ID)).thenAnswer(call -> {
            loads.incrementAndGet();
            return application;
        });
        UI.getCurrent().navigate("admin/applications/" + ID,
                QueryParameters.simple(Map.of("tab", "members", "filter", "a b")));
        var view = getCurrentView();
        UI.getCurrent().getInternals().dumpPendingJavaScriptInvocations();

        test(find(Tabs.class).single()).select("Messages");

        var location = historyLocation();
        assertThat(location.getPath()).isEqualTo("admin/applications/" + ID);
        assertThat(location.getQueryParameters().getParameters()).isEqualTo(Map.of(
                "tab", List.of("messages"), "filter", List.of("a b")));
        assertThat(getCurrentView()).isSameAs(view);
        assertThat(loads).hasValue(1);
        assertThat(test(grid("application-messages-grid")).size()).isEqualTo(1);
    }

    @Test
    void localePicker_whenLanguageChanges_reloadsSameApplicationAndTab() {
        navigate("admin/applications/" + ID, IdentityApplicationDetails.class);
        UI.getCurrent().getInternals().dumpPendingJavaScriptInvocations();
        test(find(Tabs.class).single()).select("Messages");
        var location = historyLocation();
        var picker = find(LocalePicker.class).single();

        // The picker persists the locale and requests a browser reload; re-navigation models that request.
        test(picker).selectItem(localization.i18n("locale.uk-UA"));
        UI.getCurrent().navigate(location.getPath(), location.getQueryParameters());
        var view = (IdentityApplicationDetails) getCurrentView();

        assertThat(view.getPageTitle()).isEqualTo("Деталі застосунку");
        assertThat(find(Tabs.class).single().getSelectedTab().getLabel()).isEqualTo("Повідомлення");
        assertThat(find(RouterLink.class, view).single().getHref()).contains("?tab=applications");
    }

    @Test
    void workspaceGrid_whenNameIsRendered_linksToApplicationDetailsAndKeepsSorting() {
        when(applications.findByWorkspaceUniqueId(new UniqueId(7L))).thenReturn(List.of(application));
        var tab = new IdentityWorkspaceApplicationsTab(applications, apiKeys, localization,
                new TelegramWebhookUiTasks(Runnable::run));
        UI.getCurrent().add(tab);
        tab.setWorkspace(IdentityWorkspace.builder().uniqueId(new UniqueId(7L)).name("Workspace").build());
        var grid = find(Grid.class, tab).single();
        var link = (RouterLink) test(grid).getCellComponent(0, "name");

        assertThat(link.getText()).isEqualTo("Sample app");
        assertThat(link.getHref()).isEqualTo("admin/applications/" + ID);
        assertThat(test(grid).isColumnSortable("name")).isTrue();
        test(link).click();
        assertThat(getCurrentView()).isInstanceOf(IdentityApplicationDetails.class);
    }

    @Test
    void backLink_whenClicked_returnsToWorkspaceApplicationsTabAndSupportsStaticShellTitle() {
        when(workspaces.getById(new UniqueId(7L))).thenReturn(IdentityWorkspace.builder()
                .uniqueId(new UniqueId(7L)).name("Workspace").build());
        when(applications.findByWorkspaceUniqueId(new UniqueId(7L))).thenReturn(List.of());
        var view = navigate("admin/applications/" + ID, IdentityApplicationDetails.class);

        test(find(RouterLink.class, view).single()).click();

        assertThat(getCurrentView()).isInstanceOf(IdentityWorkspaceDetails.class);
        assertThat(find(Tabs.class).single().getSelectedTab().getLabel()).isEqualTo("Applications");
        assertThat(find(H3.class).all()).anyMatch(title -> title.getText().equals("Workspace details"));
    }

    @Test
    void navigate_whenUserIsNotOwner_deniesPageBeforeDataAccess() {
        authenticate("member", "USER");
        UI.getCurrent().navigate("admin/applications/" + ID);

        assertThat(getCurrentView()).isNotInstanceOf(IdentityApplicationDetails.class);
        verifyNoInteractions(applications, members, messages);
    }

    @Test
    void navigate_whenAnonymous_redirectsToLoginWithoutLoadingData() {
        new SpringSecurityContextHandler().setupAuthentication(null);
        UI.getCurrent().navigate("admin/applications/" + ID);

        assertThat(getCurrentView()).isInstanceOf(LoginView.class);
        verifyNoInteractions(applications, members, messages);
    }

    private IdentityApplicationDetails openMessages() {
        UI.getCurrent().navigate("admin/applications/" + ID, QueryParameters.simple(Map.of("tab", "messages")));
        return (IdentityApplicationDetails) getCurrentView();
    }

    private Button dialogButton(String key) {
        return find(Button.class, find(Dialog.class).single()).all().stream()
                .filter(button -> button.getText().equals(localization.i18n("application.details.send." + key)))
                .findFirst().orElseThrow();
    }

    private Location historyLocation() {
        var paths = UI.getCurrent().getInternals().dumpPendingJavaScriptInvocations().stream()
                .flatMap(invocation -> invocation.getInvocation().getParameters().stream())
                .filter(String.class::isInstance).map(String.class::cast)
                .filter(path -> path.startsWith("admin/applications/" + ID)).toList();
        assertThat(paths).hasSize(1);
        return new Location(paths.getFirst());
    }

    private void authenticate(String username, String role) {
        var security = new SpringSecurityContextHandler();
        security.setupAuthentication(security.createCredentials(username, role));
    }

    private Grid<?> grid(String id) {
        return find(Grid.class).all().stream().filter(grid -> grid.getId().orElse("").equals(id)).findFirst().orElseThrow();
    }

    private IdentityApplicationMessage message() {
        return IdentityApplicationMessage.builder().messageUniqueId(new UniqueId(3L)).userUniqueId(new UniqueId(2L))
                .status(IdentityMessageStatus.UNKNOWN).failureCode(IdentityMessageFailureCode.DELIVERY_UNKNOWN)
                .createdAt(Instant.EPOCH).updatedAt(Instant.EPOCH).build();
    }
}
