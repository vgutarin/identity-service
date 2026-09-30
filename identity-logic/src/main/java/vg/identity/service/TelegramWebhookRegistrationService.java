package vg.identity.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.Builder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import vg.identity.model.SetTelegramWebhookCommand;
import vg.identity.model.application.TelegramBot;
import vg.identity.model.application.TelegramWebhookState;
import vg.identity.model.application.TelegramWebhookStatus;
import vg.identity.entity.IdentityApplicationEntity;
import vg.identity.repository.IdentityApplicationRepository;
import vg.identity.rest.TelegramWebhookEndpoint;
import vg.unique.id.model.UniqueId;

/**
 * Owns queued webhook setup and synchronous cleanup of a replaced application bot.
 */
@Service
public class TelegramWebhookRegistrationService {
    private final TelegramService telegramService;
    private final TelegramWebhookSecretService secretService;
    private final IdentityPublicUriBuilder publicUriBuilder;
    private final IdentityApplicationRepository applicationRepository;
    private final ObjectMapper objectMapper;
    private final TelegramBotLookupService telegramBotLookupService;
    private final IdentityCommandService commandService;
    private final boolean reregisterOnStartup;
    private final TransactionTemplate transactionTemplate;

    public TelegramWebhookRegistrationService(
            TelegramService telegramService,
            TelegramWebhookSecretService secretService,
            IdentityPublicUriBuilder publicUriBuilder,
            IdentityApplicationRepository applicationRepository,
            ObjectMapper objectMapper,
            TelegramBotLookupService telegramBotLookupService,
            IdentityCommandService commandService,
            PlatformTransactionManager transactionManager,
            @Value("${identity.telegram.webhook.reregister-on-startup:false}") boolean reregisterOnStartup
    ) {
        this.telegramService = telegramService;
        this.secretService = secretService;
        this.publicUriBuilder = publicUriBuilder;
        this.applicationRepository = applicationRepository;
        this.objectMapper = objectMapper;
        this.telegramBotLookupService = telegramBotLookupService;
        this.commandService = commandService;
        this.reregisterOnStartup = reregisterOnStartup;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Joins the application write transaction: setup is visible to the worker only after commit.
     */
    @Transactional
    public void scheduleRegistration(UniqueId applicationUniqueId) {
        if (isEnabled()) {
            commandService.enqueueTelegramWebhook(SetTelegramWebhookCommand.builder()
                    .applicationUniqueId(applicationUniqueId)
                    .build());
        }
    }

    /**
     * Configures the application's current bot, skipping deleted applications or bots without a stored ID.
     */
    @Transactional
    public void register(SetTelegramWebhookCommand command) {
        var application = applicationRepository.findById(command.applicationUniqueId().getLongValue()).orElse(null);
        if (application == null || !TelegramUriBuilder.isBotUri(application.getPrincipal().getName())) {
            return;
        }
        var telegramBot = telegramBotLookupService.readBot(application);
        if (telegramBot.telegramBotId() == null) {
            return;
        }
        requireEnabled();
        setWebhook(command.applicationUniqueId(), telegramBot);
    }

    /**
     * Only a different bot requires cleanup. A revoked old token cannot block saving its replacement.
     */
    public void removePreviousBot(TelegramBot previousBot, TelegramBot currentBot) {
        if (!isEnabled() || previousBot == null || previousBot.telegramBotId() == null) {
            return;
        }
        if (!previousBot.telegramBotId().equals(currentBot.telegramBotId())) {
            telegramService.deleteWebhook(previousBot);
        }
    }

    public void remove(TelegramBot telegramBot) {
        if (isEnabled() && telegramBot != null && telegramBot.telegramBotId() != null) {
            telegramService.deleteWebhook(telegramBot);
        }
    }

    @Transactional(readOnly = true)
    TelegramWebhookStatus inspect(UniqueId applicationUniqueId) {
        var application = getTelegramApplication(applicationUniqueId);
        return inspect(applicationUniqueId, telegramBotLookupService.readBot(application), application.getVersion());
    }

    /** Enabling backfills a missing bot ID; removal changes Telegram only. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    TelegramWebhookStatus setEnabled(UniqueId applicationUniqueId, int expectedVersion, boolean enabled) {
        requireEnabled();
        // Commit any ID backfill before Telegram can deliver updates using the newly configured secret.
        var prepared = transactionTemplate.execute(transaction -> prepareWebhook(applicationUniqueId, expectedVersion, enabled));
        var bot = prepared.bot();
        if (enabled) {
            setWebhook(applicationUniqueId, bot);
        } else {
            telegramService.deleteWebhookStrict(bot);
        }
        var status = inspect(applicationUniqueId, bot, prepared.applicationVersion());
        if (status.state() != (enabled ? TelegramWebhookState.YES : TelegramWebhookState.NO)) {
            throw new IllegalStateException("exception.telegram.webhook.changeNotConfirmed");
        }
        return status;
    }

    private PreparedWebhook prepareWebhook(UniqueId applicationUniqueId, int expectedVersion, boolean enabled) {
        var application = getTelegramApplication(applicationUniqueId);
        if (application.getVersion() != expectedVersion) {
            throw new ObjectOptimisticLockingFailureException(IdentityApplicationEntity.class, applicationUniqueId);
        }
        var bot = telegramBotLookupService.readBot(application);
        if (enabled && bot.telegramBotId() == null) {
            bot = bot.withTelegramBotId(telegramService.getBotUser(bot).id());
            requireBotId(bot);
            try {
                application.setPayload(objectMapper.writeValueAsString(bot));
            } catch (JacksonException e) {
                throw new IllegalStateException("Cannot store validated Telegram bot ID");
            }
            applicationRepository.saveAndFlush(application);
        }
        requireBotId(bot);
        return PreparedWebhook.builder().bot(bot).applicationVersion(application.getVersion()).build();
    }

    /** Re-applies the active secret, backfilling validated bot IDs on legacy encrypted payloads. */
    @Transactional
    void reregisterAll() {
        requireEnabled();
        for (var application : applicationRepository.findAllWithPrincipal()) {
            if (!TelegramUriBuilder.isBotUri(application.getPrincipal().getName())) {
                continue;
            }
            var applicationUniqueId = new UniqueId(application.getUniqueId());
            try {
                var telegramBot = telegramBotLookupService.readBot(application);
                if (telegramBot.telegramBotId() == null) {
                    telegramBot = telegramBot.withTelegramBotId(telegramService.getBotUser(telegramBot).id());
                    application.setPayload(objectMapper.writeValueAsString(telegramBot));
                    applicationRepository.saveAndFlush(application);
                }
                setWebhook(applicationUniqueId, telegramBot);
            } catch (RuntimeException e) {
                throw new IllegalStateException("Cannot re-register Telegram webhook for application " + applicationUniqueId);
            }
        }
    }

    @Transactional
    @EventListener(ApplicationReadyEvent.class)
    void reregisterWebhooksOnStartup() {
        if (reregisterOnStartup) {
            reregisterAll();
        }
    }

    private void setWebhook(UniqueId applicationUniqueId, TelegramBot telegramBot) {
        requireBotId(telegramBot);
        telegramService.setWebhook(telegramBot,
                publicUriBuilder.build(TelegramWebhookEndpoint.path(applicationUniqueId)).toString(),
                secretService.secretFor(applicationUniqueId, telegramBot.telegramBotId()));
    }

    private void requireBotId(TelegramBot telegramBot) {
        if (telegramBot.telegramBotId() == null || telegramBot.telegramBotId() <= 0) {
            throw new IllegalArgumentException("Validated telegramBotId is required");
        }
    }

    private void requireEnabled() {
        if (!isEnabled()) {
            throw new IllegalStateException("Telegram webhook registration is not enabled or has no public URL");
        }
    }

    private boolean isEnabled() {
        return publicUriBuilder.isConfigured() && secretService.isEnabled();
    }

    private IdentityApplicationEntity getTelegramApplication(UniqueId applicationUniqueId) {
        var application = applicationRepository.findById(applicationUniqueId).orElseThrow(EntityNotFoundException::new);
        if (!TelegramUriBuilder.isBotUri(application.getPrincipal().getName())) {
            throw new IllegalArgumentException("exception.telegram.webhook.notTelegramApplication");
        }
        return application;
    }

    private TelegramWebhookStatus inspect(UniqueId applicationUniqueId, TelegramBot bot, int applicationVersion) {
        if (!publicUriBuilder.isConfigured()) {
            throw new IllegalStateException("exception.telegram.webhook.publicUrlRequired");
        }
        var expectedUrl = publicUriBuilder.build(TelegramWebhookEndpoint.path(applicationUniqueId)).toString();
        var actualUrl = telegramService.getWebhookUrl(bot);
        var state = actualUrl.isEmpty() ? TelegramWebhookState.NO
                : expectedUrl.equals(actualUrl) ? TelegramWebhookState.YES : TelegramWebhookState.YES_BUT_URI_INCORRECT;
        return TelegramWebhookStatus.builder().state(state)
                .toggleAllowed(isEnabled() && (bot.telegramBotId() != null && bot.telegramBotId() > 0
                        || bot.telegramBotId() == null && state == TelegramWebhookState.NO))
                .applicationVersion(applicationVersion).build();
    }

    @Builder
    private record PreparedWebhook(TelegramBot bot, int applicationVersion) { }
}
