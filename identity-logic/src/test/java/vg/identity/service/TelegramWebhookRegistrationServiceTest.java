package vg.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;
import vg.identity.entity.IdentityApplicationEntity;
import vg.identity.entity.IdentityPrincipalEntity;
import vg.identity.model.application.TelegramBot;
import vg.identity.model.command.SetTelegramWebhookCommand;
import vg.identity.repository.IdentityApplicationRepository;
import vg.unique.id.model.UniqueId;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramWebhookRegistrationServiceTest {
    private static final UniqueId APPLICATION_ID = new UniqueId(42L);
    private static final String CALLBACK = "https://identity.example.com/api/v1/telegram/webhooks/" + APPLICATION_ID;
    @Mock private TelegramService telegramService;
    @Mock private TelegramWebhookSecretService secretService;
    @Mock private IdentityApplicationRepository applicationRepository;
    @Mock private ObjectMapper objectMapper;
    @Mock private TelegramBotLookupService telegramBotLookupService;
    @Mock private IdentityCommandService commandService;
    @Mock private PlatformTransactionManager transactionManager;
    private TelegramWebhookRegistrationService service;

    @BeforeEach
    void setUp() {
        service = service("https://identity.example.com///", false);
    }

    @Test
    void scheduleRegistration_enqueuesApplicationIdWithoutCallingTelegram() {
        when(secretService.isEnabled()).thenReturn(true);
        service.scheduleRegistration(APPLICATION_ID);
        verify(commandService).enqueueTelegramWebhook(command());
        verifyNoInteractions(telegramService);
    }

    @Test
    void scheduleRegistration_whenDisabled_doesNotEnqueue() {
        service.scheduleRegistration(APPLICATION_ID);
        verifyNoInteractions(commandService, telegramService);
    }

    @Test
    void scheduleRegistration_whenPublicOriginIsAbsent_doesNotEnqueue() {
        service("", false).scheduleRegistration(APPLICATION_ID);
        verifyNoInteractions(commandService, secretService, telegramService);
    }

    @Test
    void register_loadsTheCurrentTokenAndUsesBotBoundSecret() throws Exception {
        loadBot(bot(100L, "latest-token"));
        when(secretService.isEnabled()).thenReturn(true);
        when(secretService.secretFor(APPLICATION_ID, 100L)).thenReturn("derived-secret");
        service.register(command());
        verify(telegramService).setWebhook(bot(100L, "latest-token"), CALLBACK, "derived-secret");
    }

    @Test
    void register_usesTheCurrentBotIdAndCredentials() throws Exception {
        loadBot(bot(101L, "new-bot-token"));
        when(secretService.isEnabled()).thenReturn(true);
        when(secretService.secretFor(APPLICATION_ID, 101L)).thenReturn("new-bot-secret");
        service.register(command());
        verify(telegramService).setWebhook(bot(101L, "new-bot-token"), CALLBACK, "new-bot-secret");
    }

    @Test
    void register_whenStoredBotHasNoId_skipsSetup() throws Exception {
        loadBot(bot(null, "legacy-token"));
        service.register(command());
        verifyNoInteractions(telegramService, secretService);
    }

    @Test
    void register_whenApplicationWasDeleted_skipsTheCommand() {
        when(applicationRepository.findById(42L)).thenReturn(Optional.empty());
        service.register(command());
        verifyNoInteractions(telegramService, secretService, objectMapper, telegramBotLookupService);
    }

    @Test
    void register_whenSupportWasDisabled_failsRatherThanCompletingWithoutSetup() throws Exception {
        loadBot(bot(100L, "token"));
        assertThatIllegalStateException().isThrownBy(() -> service.register(command()))
                .withMessageContaining("not enabled");
        verifyNoInteractions(telegramService);
    }

    @Test
    void removePreviousBot_whenBotIdChanged_deletesThePreviousWebhook() {
        when(secretService.isEnabled()).thenReturn(true);
        service.removePreviousBot(bot(100L, "old-token"), bot(101L, "new-token"));
        verify(telegramService).deleteWebhook(bot(100L, "old-token"));
    }

    @Test
    void removePreviousBot_whenOnlyTokenChanged_skipsDeletion() {
        when(secretService.isEnabled()).thenReturn(true);
        service.removePreviousBot(bot(100L, "revoked-token"), bot(100L, "new-token"));
        verifyNoInteractions(telegramService);
    }

    @Test
    void removePreviousBot_whenPreviousBotHasNoId_skipsDeletionAndLookup() {
        when(secretService.isEnabled()).thenReturn(true);
        service.removePreviousBot(bot(null, "legacy-token"), bot(101L, "new-token"));
        verifyNoInteractions(telegramService);
    }

    @Test
    void remove_whenEnabled_deletesTheWebhook() {
        when(secretService.isEnabled()).thenReturn(true);
        service.remove(bot(100L, "token"));
        verify(telegramService).deleteWebhook(bot(100L, "token"));
    }

    @Test
    void remove_whenBotHasNoId_skipsDeletionAndLookup() {
        when(secretService.isEnabled()).thenReturn(true);
        service.remove(bot(null, "legacy-token"));
        verifyNoInteractions(telegramService);
    }

    @Test
    void remove_whenNotATelegramBot_skipsDeletion() {
        when(secretService.isEnabled()).thenReturn(true);
        service.remove(null);
        verifyNoInteractions(telegramService);
    }

    @Test
    void reregisterAll_whenPublicOriginIsAbsent_failsClosed() {
        assertThatIllegalStateException().isThrownBy(service("", false)::reregisterAll)
                .withMessageContaining("not enabled");
        verifyNoInteractions(applicationRepository);
    }

    @Test
    void reregisterAll_backfillsMissingBotIdBeforeRegistering() throws Exception {
        var application = application();
        var legacyBot = bot(null, "token");
        var validatedBot = bot(100L, "token");
        when(secretService.isEnabled()).thenReturn(true);
        when(applicationRepository.findAllWithPrincipal()).thenReturn(List.of(application));
        when(telegramBotLookupService.readBot(application)).thenReturn(legacyBot);
        when(telegramService.getBotUser(legacyBot)).thenReturn(TelegramApiClient.TelegramUser.builder().id(100L).build());
        when(objectMapper.writeValueAsString(validatedBot)).thenReturn("validated-payload");
        when(secretService.secretFor(APPLICATION_ID, 100L)).thenReturn("derived-secret");
        service.reregisterAll();
        assertThat(application.getPayload()).isEqualTo("validated-payload");
        var ordered = inOrder(applicationRepository, telegramService);
        ordered.verify(applicationRepository).saveAndFlush(application);
        ordered.verify(telegramService).setWebhook(validatedBot, CALLBACK, "derived-secret");
    }

    @Test
    void reregisterAll_whenPayloadIsMalformed_doesNotExposeIt() throws Exception {
        when(secretService.isEnabled()).thenReturn(true);
        var application = application();
        when(applicationRepository.findAllWithPrincipal()).thenReturn(List.of(application));
        when(telegramBotLookupService.readBot(application))
                .thenThrow(new IllegalArgumentException("sensitive-payload"));
        assertThatIllegalStateException().isThrownBy(service::reregisterAll)
                .withMessageNotContaining("sensitive-payload").withNoCause();
        verifyNoInteractions(telegramService);
    }

    @Test
    void reregisterWebhooksOnStartup_whenEnabled_reregistersStoredBots() {
        when(secretService.isEnabled()).thenReturn(true);
        when(applicationRepository.findAllWithPrincipal()).thenReturn(List.of());
        service("https://identity.example.com", true).reregisterWebhooksOnStartup();
    }

    @Test
    void reregisterWebhooksOnStartup_whenDisabled_doesNothing() {
        service.reregisterWebhooksOnStartup();
        verifyNoInteractions(applicationRepository);
    }

    private TelegramWebhookRegistrationService service(String origin, boolean startup) {
        return new TelegramWebhookRegistrationService(telegramService, secretService, new IdentityPublicUriBuilder(origin),
                applicationRepository, objectMapper, telegramBotLookupService, commandService, transactionManager, startup);
    }

    private void loadBot(TelegramBot bot) throws Exception {
        var application = application();
        when(applicationRepository.findById(42L)).thenReturn(Optional.of(application));
        when(telegramBotLookupService.readBot(application)).thenReturn(bot);
    }

    private static IdentityApplicationEntity application() {
        return IdentityApplicationEntity.builder().uniqueId(42L).payload("payload")
                .principal(IdentityPrincipalEntity.builder().name("https://t.me/test_bot").build()).build();
    }

    private static TelegramBot bot(Long id, String token) {
        return TelegramBot.builder().telegramBotId(id).token(token).build();
    }

    private static SetTelegramWebhookCommand command() {
        return SetTelegramWebhookCommand.builder().applicationUniqueId(APPLICATION_ID).build();
    }
}
