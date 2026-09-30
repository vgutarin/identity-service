package vg.identity.frontend.vaadin.admin;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import lombok.Builder;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import vg.identity.frontend.vaadin.service.LocalizationService;
import vg.identity.frontend.vaadin.service.TelegramWebhookUiTasks;
import vg.identity.frontend.vaadin.ui.Notifications;
import vg.identity.model.IdentityApplication;
import vg.identity.model.application.TelegramWebhookState;
import vg.identity.model.application.TelegramWebhookStatus;
import vg.identity.service.IdentityApplicationService;
import vg.identity.service.TelegramUriBuilder;
import vg.unique.id.model.UniqueId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** UI-owned cache and lifecycle; background submissions carry only IDs, versions, and desired states. */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class TelegramWebhookIndicators {
    private static final int POLL_INTERVAL_MS = 1000;
    private final IdentityApplicationService applicationService;
    private final TelegramWebhookUiTasks tasks;
    private final LocalizationService localization;
    private final Runnable onChange;
    private final Consumer<List<IdentityApplication>> onApplicationsReloaded;
    private final AtomicLong generation = new AtomicLong();
    private final Map<UniqueId, Display> displays = new HashMap<>();
    private List<IdentityApplication> applications = List.of();
    private long operation;
    private int pending;
    private UI pollingUi;
    private int previousPollInterval;
    private boolean changedPollInterval;

    void reset(List<IdentityApplication> applications) {
        invalidate();
        this.applications = List.copyOf(applications);
        displays.clear();
    }

    void invalidate() {
        generation.incrementAndGet();
        pending = 0;
        displays.clear();
        restorePolling();
        onChange.run();
    }

    boolean isPending() {
        return pending > 0;
    }

    void refresh(UI ui) {
        if (isPending()) {
            return;
        }
        var currentGeneration = generation.incrementAndGet();
        var ids = applications.stream().filter(this::isTelegram).map(IdentityApplication::getUniqueId).toList();
        displays.clear();
        for (var id : ids) {
            displays.put(id, Display.builder().phase(Phase.LOADING).operation(++operation).build());
        }
        if (ids.isEmpty()) {
            onChange.run();
            return;
        }
        begin(ui);
        var publish = ui.accessLater((Result result) -> {
            var current = displays.get(result.applicationId());
            if (generation.get() == currentGeneration && current != null
                    && current.operation() == result.display().operation()) {
                displays.put(result.applicationId(), result.display());
                onChange.run();
            }
        }, () -> { });
        var finish = ui.accessLater(() -> finish(currentGeneration), () -> { });
        // The worker does not access the UI-owned map or application models.
        var operations = ids.stream().map(id -> Query.builder().applicationId(id)
                .operation(displays.get(id).operation()).build()).toList();
        try {
            tasks.execute(() -> {
                try {
                    for (var query : operations) {
                        if (generation.get() != currentGeneration) {
                            break;
                        }
                        Display display;
                        try {
                            display = Display.builder().phase(Phase.READY).operation(query.operation())
                                    .status(applicationService.getTelegramWebhookStatus(query.applicationId())).build();
                        } catch (Exception ignored) {
                            display = Display.builder().phase(Phase.UNKNOWN).operation(query.operation()).build();
                        }
                        publish.accept(Result.builder().applicationId(query.applicationId()).display(display).build());
                    }
                } finally {
                    finish.run();
                }
            });
        } catch (RejectedExecutionException ignored) {
            displays.replaceAll((id, display) -> display.toBuilder().phase(Phase.UNKNOWN).build());
            finish(currentGeneration);
            Notifications.error(localization.i18n("telegram.webhook.query.failed"));
        }
    }

    Button indicator(IdentityApplication application) {
        var button = new Button();
        button.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        button.setEnabled(false);
        button.getElement().setAttribute("data-webhook-application", application.getUniqueId().toString());
        if (!isTelegram(application)) {
            button.setText(localization.i18n("telegram.webhook.notApplicable"));
            return button;
        }
        var display = displays.get(application.getUniqueId());
        if (display == null || display.phase() == Phase.UNKNOWN) {
            button.setText(localization.i18n("telegram.webhook.unknown"));
            button.setTooltipText(localization.i18n("telegram.webhook.query.failed"));
        } else if (display.phase() == Phase.LOADING || display.phase() == Phase.UPDATING) {
            button.setText(localization.i18n(display.phase() == Phase.LOADING
                    ? "telegram.webhook.loading" : "telegram.webhook.updating"));
        } else {
            var status = display.status();
            var enabled = status.state() != TelegramWebhookState.NO;
            var key = switch (status.state()) {
                case YES -> "telegram.webhook.yes";
                case NO -> "telegram.webhook.no";
                case YES_BUT_URI_INCORRECT -> "telegram.webhook.incorrect";
            };
            button.setText(localization.i18n(key));
            button.getStyle().set("color", switch (status.state()) {
                case YES -> "var(--vaadin-text-color-success, green)";
                case NO -> "var(--vaadin-text-color-secondary, gray)";
                case YES_BUT_URI_INCORRECT -> "var(--vaadin-text-color-error, darkorange)";
            });
            button.setEnabled(status.toggleAllowed());
            button.setTooltipText(localization.i18n(status.toggleAllowed()
                    ? enabled ? "telegram.webhook.remove" : "telegram.webhook.register"
                    : "telegram.webhook.readOnly"));
            var expectedOperation = display.operation();
            button.addClickListener(event -> button.getUI().ifPresent(ui ->
                    toggle(ui, application.getUniqueId(), status.applicationVersion(), expectedOperation, !enabled)));
        }
        return button;
    }

    private void toggle(UI ui, UniqueId id, int expectedVersion, long expectedOperation, boolean enabled) {
        var display = displays.get(id);
        if (display == null || display.phase() != Phase.READY || !display.status().toggleAllowed()
                || display.operation() != expectedOperation) {
            return;
        }
        var currentGeneration = generation.get();
        var currentOperation = ++operation;
        displays.put(id, Display.builder().phase(Phase.UPDATING).operation(currentOperation).build());
        begin(ui);
        var complete = ui.accessLater((ToggleResult result) -> {
            if (generation.get() != currentGeneration) {
                return;
            }
            var current = displays.get(id);
            if (current != null && current.operation() == currentOperation) {
                displays.put(id, result.display());
                if (result.application() != null) {
                    applications = applications.stream()
                            .map(application -> application.getUniqueId().equals(id) ? result.application() : application)
                            .toList();
                    onApplicationsReloaded.accept(applications);
                }
                if (result.display().phase() == Phase.READY) {
                    Notifications.success(localization.i18n("telegram.webhook.changed"));
                } else {
                    Notifications.error(localization.i18n("telegram.webhook.change.failed"));
                }
            }
            finish(currentGeneration);
        }, () -> { });
        try {
            tasks.execute(() -> {
                if (generation.get() != currentGeneration) {
                    return;
                }
                ToggleResult result;
                try {
                    var status = applicationService.setTelegramWebhookEnabled(id, expectedVersion, enabled);
                    var reloaded = status.applicationVersion() != expectedVersion ? applicationService.getById(id) : null;
                    result = ToggleResult.builder().application(reloaded)
                            .display(Display.builder().phase(Phase.READY).operation(currentOperation).status(status).build())
                            .build();
                } catch (Exception ignored) {
                    result = failedToggle(currentOperation);
                }
                complete.accept(result);
            });
        } catch (RejectedExecutionException ignored) {
            complete.accept(failedToggle(currentOperation));
        }
    }

    private ToggleResult failedToggle(long currentOperation) {
        return ToggleResult.builder()
                .display(Display.builder().phase(Phase.UNKNOWN).operation(currentOperation).build()).build();
    }

    private void begin(UI ui) {
        if (pending++ == 0) {
            pollingUi = ui;
            previousPollInterval = ui.getPollInterval();
            changedPollInterval = previousPollInterval < 0 || previousPollInterval > POLL_INTERVAL_MS;
            if (changedPollInterval) {
                ui.setPollInterval(POLL_INTERVAL_MS);
            }
        }
        onChange.run();
    }

    private void finish(long currentGeneration) {
        if (generation.get() != currentGeneration) {
            return;
        }
        if (--pending == 0) {
            restorePolling();
        }
        onChange.run();
    }

    private void restorePolling() {
        if (pollingUi != null && changedPollInterval && pollingUi.getPollInterval() == POLL_INTERVAL_MS) {
            pollingUi.setPollInterval(previousPollInterval);
        }
        pollingUi = null;
        changedPollInterval = false;
    }

    private boolean isTelegram(IdentityApplication application) {
        return TelegramUriBuilder.isBotUri(application.getUri());
    }

    private enum Phase { LOADING, UPDATING, UNKNOWN, READY }

    @Builder(toBuilder = true)
    private record Display(Phase phase, long operation, TelegramWebhookStatus status) { }

    @Builder
    private record Query(UniqueId applicationId, long operation) { }

    @Builder
    private record Result(UniqueId applicationId, Display display) { }

    @Builder
    private record ToggleResult(Display display, IdentityApplication application) { }
}
