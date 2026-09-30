package vg.identity.frontend.vaadin.admin;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vg.identity.frontend.vaadin.service.LocalizationService;
import vg.identity.frontend.vaadin.service.TelegramWebhookUiTasks;
import vg.identity.model.IdentityApplication;
import vg.identity.model.IdentityWorkspace;
import vg.identity.model.application.TelegramBot;
import vg.identity.model.application.TelegramWebhookState;
import vg.identity.model.application.TelegramWebhookStatus;
import vg.identity.service.IdentityApiKeyService;
import vg.identity.service.IdentityApplicationService;
import vg.unique.id.model.UniqueId;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:fe-webhook-test;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("test")
class TelegramWebhookIndicatorsTest extends SpringBrowserlessTest {
    @MockitoBean private IdentityApplicationService applications;
    @MockitoBean private IdentityApiKeyService apiKeys;
    @Autowired private LocalizationService localization;
    private Queue<Runnable> queue;
    private TelegramWebhookIndicators indicators;
    private IdentityApplication application;

    @BeforeEach
    void setUp() {
        queue = new ArrayDeque<>();
        localization.setCurrentLocale(Locale.ENGLISH);
        application = IdentityApplication.builder().uniqueId(new UniqueId(42L)).version(7)
                .name("Bot").uri("https://t.me/test_bot").build();
        indicators = new TelegramWebhookIndicators(applications, new TelegramWebhookUiTasks(queue::add),
                localization, () -> { }, reloaded -> { });
        indicators.reset(List.of(application));
        UI.getCurrent().setPollInterval(-1);
    }

    @Test
    void refresh_isOneBackgroundBatchWithLoadingAndNoRenderingQueries() {
        indicators.refresh(UI.getCurrent());
        indicators.refresh(UI.getCurrent());
        assertThat(indicator().getText()).isEqualTo("Loading");
        assertThat(indicator().isEnabled()).isFalse();
        assertThat(queue).hasSize(1);
        verifyNoInteractions(applications);
        assertThat(UI.getCurrent().getPollInterval()).isEqualTo(1000);
        when(applications.getTelegramWebhookStatus(application.getUniqueId())).thenReturn(status(TelegramWebhookState.NO));

        runNext();

        assertThat(indicator().getText()).isEqualTo("No");
        assertThat(indicators.isPending()).isFalse();
        assertThat(UI.getCurrent().getPollInterval()).isEqualTo(-1);
        verify(applications, times(1)).getTelegramWebhookStatus(application.getUniqueId());
    }

    @ParameterizedTest
    @EnumSource(TelegramWebhookState.class)
    void toggle_selectsExplicitDesiredStateAndPreventsDuplicateClicks(TelegramWebhookState state) {
        load(state);
        var button = indicator();
        assertThat(button.getText()).isEqualTo(switch (state) {
            case YES -> "Yes";
            case NO -> "No";
            case YES_BUT_URI_INCORRECT -> "Yes, incorrect URL";
        });
        var desired = state == TelegramWebhookState.NO;
        when(applications.setTelegramWebhookEnabled(application.getUniqueId(), 7, desired))
                .thenReturn(status(desired ? TelegramWebhookState.YES : TelegramWebhookState.NO));
        test(button).click();
        test(button).click();
        assertThat(queue).hasSize(1);
        assertThat(indicator().getText()).isEqualTo("Updating");
        assertThat(indicator().isEnabled()).isFalse();
        verify(applications, never()).setTelegramWebhookEnabled(any(), anyInt(), anyBoolean());

        runNext();

        verify(applications).setTelegramWebhookEnabled(application.getUniqueId(), 7, desired);
        assertThat(indicator().getText()).isEqualTo(desired ? "Yes" : "No");
        assertThat(UI.getCurrent().getPollInterval()).isEqualTo(-1);
    }

    @Test
    void toggle_afterBotIdBackfill_usesTheConfirmedApplicationVersionForTheNextAction() {
        load(TelegramWebhookState.NO);
        when(applications.getById(application.getUniqueId())).thenReturn(IdentityApplication.builder()
                .uniqueId(application.getUniqueId()).version(8).name(application.getName()).uri(application.getUri()).build());
        when(applications.setTelegramWebhookEnabled(application.getUniqueId(), 7, true))
                .thenReturn(status(TelegramWebhookState.YES).toBuilder().applicationVersion(8).build());
        test(indicator()).click();
        runNext();
        assertThat(indicator().getText()).isEqualTo("Yes");
        assertThat(indicator().isEnabled()).isTrue();
        when(applications.setTelegramWebhookEnabled(application.getUniqueId(), 8, false))
                .thenReturn(status(TelegramWebhookState.NO).toBuilder().applicationVersion(8).build());
        test(indicator()).click();
        runNext();
        verify(applications).setTelegramWebhookEnabled(application.getUniqueId(), 8, false);
        assertThat(indicator().getText()).isEqualTo("No");
    }

    @Test
    void queryFailure_isUnknownAndDoesNotExposeErrorDetails() {
        when(applications.getTelegramWebhookStatus(any())).thenThrow(new IllegalStateException("sensitive remote body"));
        indicators.refresh(UI.getCurrent());
        runNext();
        assertThat(indicator().getText()).isEqualTo("Unknown");
        assertThat(indicator().isEnabled()).isFalse();
        assertThat(indicator().getTooltip().getText()).doesNotContain("sensitive");
        assertThat(UI.getCurrent().getPollInterval()).isEqualTo(-1);
    }

    @Test
    void mutationFailure_isUnknownAndIsNeverRetried() {
        load(TelegramWebhookState.NO);
        when(applications.setTelegramWebhookEnabled(any(), anyInt(), anyBoolean()))
                .thenThrow(new IllegalStateException("sensitive remote body"));
        test(indicator()).click();
        runNext();
        assertThat(indicator().getText()).isEqualTo("Unknown");
        assertThat(indicator().isEnabled()).isFalse();
        assertThat(queue).isEmpty();
        verify(applications, times(1)).setTelegramWebhookEnabled(application.getUniqueId(), 7, true);
        assertThat(UI.getCurrent().getPollInterval()).isEqualTo(-1);
    }

    @Test
    void staleBatch_stopsBeforeNextQueryAndCannotOverwriteNewResults() {
        var second = IdentityApplication.builder().uniqueId(new UniqueId(43L)).uri(application.getUri()).build();
        indicators.reset(List.of(application, second));
        when(applications.getTelegramWebhookStatus(application.getUniqueId())).thenAnswer(invocation -> {
            indicators.reset(List.of(application)); // Simulate a bot edit/navigation while Telegram is responding.
            return status(TelegramWebhookState.YES);
        });
        indicators.refresh(UI.getCurrent());
        runNext();
        verify(applications, never()).getTelegramWebhookStatus(second.getUniqueId());
        assertThat(indicator().getText()).isEqualTo("Unknown");
        assertThat(UI.getCurrent().getPollInterval()).isEqualTo(-1);
        when(applications.getTelegramWebhookStatus(application.getUniqueId())).thenReturn(status(TelegramWebhookState.NO));
        indicators.refresh(UI.getCurrent());
        runNext();
        assertThat(indicator().getText()).isEqualTo("No");
    }

    @Test
    void queuedToggle_isAbandonedAfterInvalidation() {
        load(TelegramWebhookState.NO);
        test(indicator()).click();
        indicators.invalidate();
        runNext();
        verify(applications, never()).setTelegramWebhookEnabled(any(), anyInt(), anyBoolean());
        assertThat(indicators.isPending()).isFalse();
        assertThat(UI.getCurrent().getPollInterval()).isEqualTo(-1);
    }

    @Test
    void staleButton_afterTabInvalidationCannotStartAToggle() {
        load(TelegramWebhookState.NO);
        var oldButton = indicator();
        indicators.invalidate();
        test(oldButton).click();
        assertThat(queue).isEmpty();
        assertThat(indicator().getText()).isEqualTo("Unknown");
        verify(applications, never()).setTelegramWebhookEnabled(any(), anyInt(), anyBoolean());
    }

    @Test
    void executorRejection_isUnknownWithoutDoingWorkOnTheUiThread() {
        indicators = new TelegramWebhookIndicators(applications, new TelegramWebhookUiTasks(task -> {
            throw new RejectedExecutionException();
        }), localization, () -> { }, reloaded -> { });
        indicators.reset(List.of(application));
        indicators.refresh(UI.getCurrent());
        assertThat(indicator().getText()).isEqualTo("Unknown");
        assertThat(indicator().isEnabled()).isFalse();
        assertThat(indicators.isPending()).isFalse();
        assertThat(UI.getCurrent().getPollInterval()).isEqualTo(-1);
        verifyNoInteractions(applications);
    }

    @Test
    void polling_preservesAnExistingFasterInterval() {
        UI.getCurrent().setPollInterval(500);
        load(TelegramWebhookState.YES);
        assertThat(UI.getCurrent().getPollInterval()).isEqualTo(500);
    }

    @Test
    void polling_restoresAnExistingSlowerInterval() {
        UI.getCurrent().setPollInterval(5000);
        when(applications.getTelegramWebhookStatus(any())).thenReturn(status(TelegramWebhookState.YES));
        indicators.refresh(UI.getCurrent());
        assertThat(UI.getCurrent().getPollInterval()).isEqualTo(1000);
        runNext();
        assertThat(UI.getCurrent().getPollInterval()).isEqualTo(5000);
    }

    @Test
    void mutationRejection_becomesUnknownAndRestoresPolling() {
        var reject = new java.util.concurrent.atomic.AtomicBoolean();
        indicators = new TelegramWebhookIndicators(applications, new TelegramWebhookUiTasks(task -> {
            if (reject.get()) { throw new RejectedExecutionException(); }
            queue.add(task);
        }), localization, () -> { }, reloaded -> { });
        indicators.reset(List.of(application));
        load(TelegramWebhookState.NO);
        reject.set(true);
        test(indicator()).click();
        var session = UI.getCurrent().getSession();
        session.getService().runPendingAccessTasks(session);
        assertThat(indicator().getText()).isEqualTo("Unknown");
        assertThat(indicator().isEnabled()).isFalse();
        assertThat(indicators.isPending()).isFalse();
        assertThat(UI.getCurrent().getPollInterval()).isEqualTo(-1);
        verify(applications, never()).setTelegramWebhookEnabled(any(), anyInt(), anyBoolean());
    }

    @Test
    void alreadyStartedMutation_mayCompleteButItsStaleResultIsDiscarded() {
        load(TelegramWebhookState.NO);
        when(applications.setTelegramWebhookEnabled(any(), anyInt(), anyBoolean())).thenAnswer(invocation -> {
            indicators.reset(List.of(application));
            return status(TelegramWebhookState.YES);
        });
        test(indicator()).click();
        runNext();
        verify(applications).setTelegramWebhookEnabled(application.getUniqueId(), 7, true);
        assertThat(indicator().getText()).isEqualTo("Unknown");
        assertThat(indicators.isPending()).isFalse();
        assertThat(UI.getCurrent().getPollInterval()).isEqualTo(-1);
    }

    @Test
    void refresh_publishesEachResultBeforeTheNextQueryCompletes() {
        var second = IdentityApplication.builder().uniqueId(new UniqueId(43L)).uri(application.getUri()).build();
        indicators.reset(List.of(application, second));
        when(applications.getTelegramWebhookStatus(application.getUniqueId())).thenReturn(status(TelegramWebhookState.YES));
        when(applications.getTelegramWebhookStatus(second.getUniqueId())).thenAnswer(invocation -> {
            var session = UI.getCurrent().getSession();
            session.getService().runPendingAccessTasks(session);
            assertThat(indicator().getText()).isEqualTo("Yes");
            assertThat(indicators.indicator(second).getText()).isEqualTo("Loading");
            assertThat(indicators.isPending()).isTrue();
            return status(TelegramWebhookState.NO);
        });
        indicators.refresh(UI.getCurrent());
        runNext();
        assertThat(indicators.indicator(second).getText()).isEqualTo("No");
        assertThat(indicators.isPending()).isFalse();
    }

    @Test
    void unsupportedApplicationAndReadOnlyStatus_disableToggling() {
        var other = IdentityApplication.builder().uniqueId(application.getUniqueId()).uri("https://example.test").build();
        assertThat(indicators.indicator(other).getText()).isEqualTo("Not applicable");
        when(applications.getTelegramWebhookStatus(any())).thenReturn(TelegramWebhookStatus.builder()
                .state(TelegramWebhookState.YES).toggleAllowed(false).build());
        indicators.refresh(UI.getCurrent());
        runNext();
        assertThat(indicator().isEnabled()).isFalse();
        assertThat(indicator().getText()).isEqualTo("Yes");
    }

    @Test
    void applicationsTab_queriesOnlyWhenActiveAndRestoresPollingOnDeactivateAndDetach() {
        var workspace = IdentityWorkspace.builder().uniqueId(new UniqueId(100L)).build();
        when(applications.findByWorkspaceUniqueId(workspace.getUniqueId())).thenReturn(List.of(application));
        var tab = new IdentityWorkspaceApplicationsTab(applications, apiKeys, localization,
                new TelegramWebhookUiTasks(queue::add));
        UI.getCurrent().add(tab);
        tab.setWorkspace(workspace);
        assertThat(queue).isEmpty();
        tab.setActive(true);
        assertThat(queue).hasSize(1);
        assertThat(UI.getCurrent().getPollInterval()).isEqualTo(1000);
        tab.setActive(false);
        assertThat(UI.getCurrent().getPollInterval()).isEqualTo(-1);
        runNext();
        verify(applications, never()).getTelegramWebhookStatus(any());
        tab.setActive(true);
        UI.getCurrent().remove(tab);
        assertThat(UI.getCurrent().getPollInterval()).isEqualTo(-1);
        runNext();
        verify(applications, never()).getTelegramWebhookStatus(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void applicationsGrid_exposesCachedIndicatorAndManualRefresh() {
        var workspace = IdentityWorkspace.builder().uniqueId(new UniqueId(100L)).build();
        when(applications.findByWorkspaceUniqueId(workspace.getUniqueId())).thenReturn(List.of(application));
        when(applications.getTelegramWebhookStatus(any())).thenReturn(status(TelegramWebhookState.YES));
        var tab = new IdentityWorkspaceApplicationsTab(applications, apiKeys, localization,
                new TelegramWebhookUiTasks(queue::add));
        UI.getCurrent().add(tab);
        tab.setActive(true);
        tab.setWorkspace(workspace);
        var grid = (Grid<IdentityApplication>) find(Grid.class).single();
        assertThat(((Button) test(grid).getCellComponent(0, "webhook")).getText()).isEqualTo("Loading");
        var refresh = find(Button.class).all().stream().filter(b -> b.getText().equals("Refresh webhooks")).findFirst().orElseThrow();
        assertThat(refresh.isEnabled()).isFalse();
        runNext();
        assertThat(((Button) test(grid).getCellComponent(0, "webhook")).getText()).isEqualTo("Yes");
        assertThat(refresh.isEnabled()).isTrue();
        test(refresh).click();
        assertThat(queue).hasSize(1);
        runNext();
        verify(applications, times(2)).getTelegramWebhookStatus(application.getUniqueId());
    }

    @Test
    @SuppressWarnings("unchecked")
    void applicationsGrid_afterBotIdBackfill_editsTheReloadedModelWithItsCurrentVersion() {
        var workspace = IdentityWorkspace.builder().uniqueId(new UniqueId(100L)).build();
        var reloaded = IdentityApplication.builder().uniqueId(application.getUniqueId()).version(8)
                .name("Reloaded bot").uri(application.getUri()).build();
        when(applications.findByWorkspaceUniqueId(workspace.getUniqueId())).thenReturn(List.of(application));
        when(applications.getTelegramWebhookStatus(application.getUniqueId())).thenReturn(status(TelegramWebhookState.NO));
        when(applications.setTelegramWebhookEnabled(application.getUniqueId(), 7, true))
                .thenReturn(status(TelegramWebhookState.YES).toBuilder().applicationVersion(8).build());
        when(applications.getById(application.getUniqueId())).thenReturn(reloaded);
        var tab = new IdentityWorkspaceApplicationsTab(applications, apiKeys, localization,
                new TelegramWebhookUiTasks(queue::add));
        UI.getCurrent().add(tab);
        tab.setActive(true);
        tab.setWorkspace(workspace);
        runNext();
        var grid = (Grid<IdentityApplication>) find(Grid.class).single();

        test((Button) test(grid).getCellComponent(0, "webhook")).click();
        runNext();

        assertThat(grid.getListDataView().getItems()).containsExactly(reloaded);
        assertThat(((Button) test(grid).getCellComponent(0, "webhook")).getText()).isEqualTo("Yes");
        assertThat(queue).isEmpty();
        var rowActions = test(grid).getCellComponent(0, grid.getColumns().size() - 1);
        var edit = rowActions.getChildren().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getText().equals("Edit")).findFirst().orElseThrow();
        test(edit).click();
        var name = find(TextField.class).single();
        assertThat(name.getValue()).isEqualTo("Reloaded bot");
        name.setValue("Edited bot");
        find(PasswordField.class).single().setValue("replacement-token");
        var save = find(Button.class).all().stream().filter(button -> button.getText().equals("Save")).findFirst().orElseThrow();
        test(save).click();

        verify(applications).updateTelegramBotApplication(application.getUniqueId(), 8, "Edited bot",
                TelegramBot.builder().token("replacement-token").build());
    }

    private void load(TelegramWebhookState state) {
        when(applications.getTelegramWebhookStatus(application.getUniqueId())).thenReturn(status(state));
        indicators.refresh(UI.getCurrent());
        runNext();
    }

    private Button indicator() {
        var button = indicators.indicator(application);
        UI.getCurrent().add(button);
        return button;
    }

    private void runNext() {
        queue.remove().run();
        var session = UI.getCurrent().getSession();
        session.getService().runPendingAccessTasks(session);
        roundTrip();
    }

    private TelegramWebhookStatus status(TelegramWebhookState state) {
        return TelegramWebhookStatus.builder().state(state).toggleAllowed(true).applicationVersion(7).build();
    }
}
