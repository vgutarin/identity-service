package vg.identity.frontend.vaadin.admin;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vg.identity.frontend.vaadin.service.LocalizationService;
import vg.identity.frontend.vaadin.service.TelegramWebhookUiTasks;
import vg.identity.model.IdentityApplication;
import vg.identity.model.application.TelegramWebhookState;
import vg.identity.model.application.TelegramWebhookStatus;
import vg.identity.service.IdentityApplicationService;
import vg.unique.id.model.UniqueId;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ViewPackages(classes = TelegramWebhookIndicators.class)
class TelegramWebhookIndicatorsUnitTest extends BrowserlessTest {
    @Mock private IdentityApplicationService service;
    @Mock private LocalizationService localization;
    @Mock private Consumer<List<IdentityApplication>> onApplicationsReloaded;
    @Captor private ArgumentCaptor<List<IdentityApplication>> models;

    private Queue<Runnable> tasks;
    private TelegramWebhookIndicators indicators;
    private IdentityApplication application;

    @BeforeEach
    void setUp() {
        tasks = new ArrayDeque<>();
        application = bot(7);
        when(localization.i18n(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        indicators = new TelegramWebhookIndicators(service, new TelegramWebhookUiTasks(tasks::add),
                localization, () -> { }, onApplicationsReloaded);
        indicators.reset(List.of(application));
        when(service.getTelegramWebhookStatus(application.getUniqueId())).thenReturn(status(TelegramWebhookState.NO, 7));
        indicators.refresh(UI.getCurrent());
        runNext();
    }

    @Test
    void toggle_whenVersionChanges_publishesTheReloadedModelAndPreservesOtherRows() {
        var other = IdentityApplication.builder().uniqueId(new UniqueId(43L)).uri("https://example.test").build();
        indicators.reset(List.of(application, other));
        indicators.refresh(UI.getCurrent());
        runNext();
        var reloaded = bot(8);
        when(service.setTelegramWebhookEnabled(application.getUniqueId(), 7, true))
                .thenReturn(status(TelegramWebhookState.YES, 8));
        when(service.getById(application.getUniqueId())).thenReturn(reloaded);

        clickIndicator();
        runNext();

        verify(onApplicationsReloaded).accept(models.capture());
        assertThat(models.getValue()).containsExactly(reloaded, other);
        assertThat(indicators.isPending()).isFalse();
        assertThat(tasks).isEmpty();
    }

    @Test
    void toggle_whenReloadBecomesObsolete_doesNotPublishTheOldWorkspaceModel() {
        when(service.setTelegramWebhookEnabled(application.getUniqueId(), 7, true))
                .thenReturn(status(TelegramWebhookState.YES, 8));
        when(service.getById(application.getUniqueId())).thenAnswer(invocation -> {
            indicators.reset(List.of());
            return bot(8);
        });

        clickIndicator();
        runNext();

        verifyNoInteractions(onApplicationsReloaded);
        assertThat(indicators.isPending()).isFalse();
    }

    @Test
    void toggle_whenReloadFails_showsUnknownAndDoesNotRetryTheMutation() {
        when(service.setTelegramWebhookEnabled(application.getUniqueId(), 7, true))
                .thenReturn(status(TelegramWebhookState.YES, 8));
        when(service.getById(application.getUniqueId())).thenThrow(new IllegalStateException("read failed"));

        clickIndicator();
        runNext();

        verifyNoInteractions(onApplicationsReloaded);
        assertThat(indicators.indicator(application).getText()).isEqualTo("telegram.webhook.unknown");
        assertThat(indicators.isPending()).isFalse();
        assertThat(tasks).isEmpty();
    }

    private void clickIndicator() {
        Button button = indicators.indicator(application);
        UI.getCurrent().add(button);
        test(button).click();
    }

    private void runNext() {
        tasks.remove().run();
        var session = UI.getCurrent().getSession();
        session.getService().runPendingAccessTasks(session);
        roundTrip();
    }

    private IdentityApplication bot(int version) {
        return IdentityApplication.builder().uniqueId(new UniqueId(42L)).version(version)
                .name("Bot " + version).uri("https://t.me/test_bot").build();
    }

    private TelegramWebhookStatus status(TelegramWebhookState state, int version) {
        return TelegramWebhookStatus.builder().state(state).toggleAllowed(true).applicationVersion(version).build();
    }
}
