package vg.identity.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;
import tools.jackson.databind.ObjectMapper;
import vg.identity.BaseIntegrationTest;
import vg.identity.model.IdentityApplication;
import vg.identity.model.command.IdentityCommandStatus;
import vg.identity.model.command.SetTelegramWebhookCommand;
import vg.identity.model.access.Permission;
import vg.identity.model.application.TelegramBot;
import vg.unique.id.model.UniqueId;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@TestPropertySource(properties = {
        "identity.telegram.webhook.enabled=true",
        "identity.telegram.webhook.secret=integration-test-webhook-secret-at-least-32-characters",
        "identity.service.public-url=https://identity.example.test"
})
@WithMockUser(username = "webhook-admin", roles = "USER")
class TelegramWebhookLifecycleIntegrationTest extends BaseIntegrationTest {
    @Autowired private IdentityApplicationService applicationService;
    @Autowired private TelegramWebhookRegistrationService registrationService;
    @Autowired private TelegramWebhookSecretService secretService;
    @Autowired private IdentityCommandService commandService;
    @Autowired private EmailService emailService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private IdentityMessageCommandProcessor messageProcessor;
    @MockitoBean private TelegramApiClient telegramApiClient;

    @Test
    void create_savesValidatedBotAndIdentifierOnlyCommand_thenWorkerSetsWebhook() throws Exception {
        var application = createBot("current-token", 100L);
        var stored = applicationRepository.findById(application.getUniqueId()).orElseThrow();
        assertThat(objectMapper.readValue(stored.getPayload(), TelegramBot.class).telegramBotId()).isEqualTo(100L);
        var queued = commandRepository.findAll().getFirst();
        assertThat(queued.getCommandStatus()).isEqualTo(IdentityCommandStatus.QUEUED);
        var payload = objectMapper.readTree(queued.getPayload());
        assertThat(payload.size()).isEqualTo(1);
        assertThat(payload.path("applicationUniqueId").asText()).isEqualTo(application.getUniqueId().toString());
        assertThat(queued.getPayload()).doesNotContain("current-token", "secret");
        verify(telegramApiClient, never()).setWebhook(anyString(), anyString(), anyString());
        var usedToken = successfulSetup();

        worker().processNextQueuedCommand();

        assertThat(usedToken.get()).isEqualTo("current-token");
        assertThat(commandRepository.findById(queued.getId()).orElseThrow().getCommandStatus())
                .isEqualTo(IdentityCommandStatus.COMPLETED);
    }

    @Test
    void create_andEnqueue_rollBackTogether() {
        var workspaceId = adminWorkspace();
        validToken("token", 100L);
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            applicationService.createTelegramBotApplication(workspaceId, "bot", bot("token"));
            throw new IllegalStateException("rollback");
        })).hasMessage("rollback");
        assertThat(applicationRepository.count()).isZero();
        assertThat(commandRepository.count()).isZero();
    }

    @Test
    void update_sameBotTokenRotation_skipsCleanupAndWorkerLoadsLatestToken() throws Exception {
        var application = createBot("revoked-token", 100L);
        validToken("new-token", 100L);
        applicationService.updateTelegramBotApplication(application.getUniqueId(), application.getVersion(), "rotated", bot("new-token"));
        verify(telegramApiClient, never()).deleteWebhook(anyString());
        var usedToken = successfulSetup();

        worker().processNextQueuedCommand();

        assertThat(usedToken.get()).isEqualTo("new-token");
        assertThat(commandRepository.findAll()).allMatch(command -> command.getCommandStatus() == IdentityCommandStatus.COMPLETED);
    }

    @Test
    void update_differentBotWithRevokedOldToken_bothQueuedCommandsConfigureCurrentBot() throws Exception {
        var application = createBot("revoked-token", 100L);
        var oldSecret = secretService.secretFor(application.getUniqueId(), 100L);
        validToken("replacement-token", 101L);
        when(telegramApiClient.deleteWebhook("revoked-token"))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));
        applicationService.updateTelegramBotApplication(application.getUniqueId(), application.getVersion(), "replacement", bot("replacement-token"));
        var usedToken = successfulSetup();

        worker().processNextQueuedCommand();

        assertThat(usedToken.get()).isEqualTo("replacement-token");
        verify(telegramApiClient, times(2)).setWebhook(eq("replacement-token"), anyString(),
                eq(secretService.secretFor(application.getUniqueId(), 101L)));
        verify(telegramApiClient, never()).setWebhook(eq("revoked-token"), anyString(), anyString());
        assertThat(storedBot(application).telegramBotId()).isEqualTo(101L);
        assertThat(secretService.matches(application.getUniqueId(), 101L, oldSecret)).isFalse();
        assertThat(commandRepository.findAll()).hasSize(2)
                .allMatch(command -> command.getCommandStatus() == IdentityCommandStatus.COMPLETED);
    }

    @Test
    void update_differentBotWithNon401CleanupFailure_keepsOldConfigurationAndDoesNotEnqueue() throws Exception {
        var application = createBot("old-token", 100L);
        validToken("replacement-token", 101L);
        when(telegramApiClient.deleteWebhook("old-token"))
                .thenThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> applicationService.updateTelegramBotApplication(application.getUniqueId(),
                application.getVersion(), "replacement", bot("replacement-token")))
                .hasMessage("exception.telegram.deleteWebhook.failed");

        assertThat(storedBot(application).token()).isEqualTo("old-token");
        assertThat(commandRepository.count()).isEqualTo(1);
    }

    @Test
    void update_previousBotWithoutId_skipsCleanupAndPersistsNewValidatedId() throws Exception {
        var application = createBot("legacy-token", 100L);
        var stored = applicationRepository.findById(application.getUniqueId()).orElseThrow();
        stored.setPayload("{\"token\":\"legacy-token\"}");
        stored = applicationRepository.saveAndFlush(stored);
        validToken("replacement-token", 101L);

        applicationService.updateTelegramBotApplication(application.getUniqueId(), stored.getVersion(), "replacement", bot("replacement-token"));

        assertThat(storedBot(application).telegramBotId()).isEqualTo(101L);
        verify(telegramApiClient, never()).deleteWebhook(anyString());
    }

    @Test
    void delete_botWithoutId_removesApplicationWithoutCallingTelegram() {
        var application = createBot("legacy-token", 100L);
        var stored = applicationRepository.findById(application.getUniqueId()).orElseThrow();
        stored.setPayload("{\"token\":\"legacy-token\"}");
        applicationRepository.saveAndFlush(stored);

        applicationService.delete(application.getUniqueId());

        assertThat(applicationRepository.findById(application.getUniqueId())).isEmpty();
        verify(telegramApiClient, never()).deleteWebhook(anyString());
    }

    @Test
    void reregisterAll_backfillsLegacyBotIdAndSetsBotBoundSecret() throws Exception {
        var application = createBot("legacy-token", 100L);
        var stored = applicationRepository.findById(application.getUniqueId()).orElseThrow();
        stored.setPayload("{\"token\":\"legacy-token\"}");
        applicationRepository.saveAndFlush(stored);
        successfulSetup();

        registrationService.reregisterAll();

        assertThat(storedBot(application).telegramBotId()).isEqualTo(100L);
        verify(telegramApiClient).setWebhook(eq("legacy-token"), anyString(),
                eq(secretService.secretFor(application.getUniqueId(), 100L)));
    }

    @Test
    void update_whenCallerRollsBackAfterCleanup_keepsOldConfigurationAndCommandCount() throws Exception {
        var application = createBot("old-token", 100L);
        validToken("replacement-token", 101L);
        when(telegramApiClient.deleteWebhook("old-token"))
                .thenReturn(TelegramApiClient.SetWebhookResponse.builder().ok(true).build());

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            applicationService.updateTelegramBotApplication(application.getUniqueId(), application.getVersion(), "replacement", bot("replacement-token"));
            throw new IllegalStateException("rollback");
        })).hasMessage("rollback");

        assertThat(storedBot(application).telegramBotId()).isEqualTo(100L);
        assertThat(commandRepository.count()).isEqualTo(1);
        // Telegram cleanup cannot roll back; a retry must accept an already-deleted webhook.
        applicationService.updateTelegramBotApplication(application.getUniqueId(), application.getVersion(), "replacement", bot("replacement-token"));
        assertThat(storedBot(application).telegramBotId()).isEqualTo(101L);
    }

    @Test
    void setupFailure_marksCommandFailedWithoutPersistingTokenOrUrl() {
        createBot("current-token", 100L);
        when(telegramApiClient.setWebhook(eq("current-token"), anyString(), anyString()))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED, "https://api.telegram.org/botcurrent-token/setWebhook"));

        worker().processNextQueuedCommand();

        var failed = commandRepository.findAll().getFirst();
        assertThat(failed.getCommandStatus()).isEqualTo(IdentityCommandStatus.FAILED);
        assertThat(failed.getErrorMessage()).doesNotContain("current-token", "api.telegram.org", "Unauthorized");
    }

    @Test
    void queuedSetup_afterApplicationDeletion_doesNothing() throws Exception {
        var application = createBot("token", 100L);
        var command = objectMapper.readValue(commandRepository.findAll().getFirst().getPayload(), SetTelegramWebhookCommand.class);
        applicationRepository.deleteById(application.getUniqueId().getLongValue());

        registrationService.register(command);

        verify(telegramApiClient, never()).setWebhook(anyString(), anyString(), anyString());
    }

    private IdentityApplication createBot(String token, long botId) {
        var workspaceId = adminWorkspace();
        validToken(token, botId);
        // Client-supplied IDs are ignored: getMe is authoritative.
        return applicationService.createTelegramBotApplication(workspaceId, "bot", bot(token).withTelegramBotId(999L));
    }

    private UniqueId adminWorkspace() {
        var admin = createIdentityUser("webhook-admin");
        var workspace = createWorkspace();
        grantWorkspacePermission(admin, workspace, Permission.App.CREATE);
        grantWorkspacePermission(admin, workspace, Permission.App.UPDATE);
        grantWorkspacePermission(admin, workspace, Permission.App.DELETE);
        return new UniqueId(workspace.getUniqueId());
    }

    private void validToken(String token, long botId) {
        when(telegramApiClient.getMe(token)).thenReturn(TelegramApiClient.GetMeResponse.builder().ok(true)
                .result(TelegramApiClient.TelegramUser.builder().id(botId).bot(true).username("test_bot_" + botId).build()).build());
    }

    private AtomicReference<String> successfulSetup() {
        var usedToken = new AtomicReference<String>();
        when(telegramApiClient.setWebhook(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            usedToken.set(invocation.getArgument(0, String.class));
            return TelegramApiClient.SetWebhookResponse.builder().ok(true).build();
        });
        return usedToken;
    }

    private TelegramBot storedBot(IdentityApplication application) throws Exception {
        return objectMapper.readValue(applicationRepository.findById(application.getUniqueId()).orElseThrow().getPayload(), TelegramBot.class);
    }

    private IdentityCommandWorker worker() {
        return new IdentityCommandWorker(commandService, emailService, objectMapper, registrationService, messageProcessor);
    }

    private static TelegramBot bot(String token) {
        return TelegramBot.builder().token(token).build();
    }
}
