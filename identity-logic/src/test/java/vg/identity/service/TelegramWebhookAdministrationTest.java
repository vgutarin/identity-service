package vg.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import vg.identity.entity.IdentityApplicationEntity;
import vg.identity.entity.IdentityPrincipalEntity;
import vg.identity.model.application.TelegramBot;
import vg.identity.model.application.TelegramWebhookState;
import vg.identity.repository.IdentityApplicationRepository;
import vg.identity.rest.TelegramWebhookEndpoint;
import vg.unique.id.model.UniqueId;

import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramWebhookAdministrationTest {
    private static final UniqueId APPLICATION_ID = new UniqueId(42L);
    private static final String EXPECTED_URL = "https://identity.example.test/identity" + TelegramWebhookEndpoint.path(APPLICATION_ID);
    @Mock private TelegramService telegramService;
    @Mock private TelegramWebhookSecretService secretService;
    @Mock private IdentityApplicationRepository repository;
    @Mock private TelegramBotLookupService lookup;
    @Mock private ObjectMapper mapper;
    @Mock private IdentityCommandService commands;
    @Mock
    private JacksonException serializationFailure;
    @Mock
    private PlatformTransactionManager transactionManager;
    private TelegramWebhookRegistrationService service;
    private IdentityApplicationEntity application;
    private TelegramBot bot;

    @BeforeEach
    void setUp() {
        service = service("https://identity.example.test/identity");
        application = IdentityApplicationEntity.builder().uniqueId(42L).version(3)
                .principal(IdentityPrincipalEntity.builder().name("https://t.me/test_bot").build()).build();
        bot = TelegramBot.builder().telegramBotId(100L).token("test-token").build();
    }

    @ParameterizedTest
    @EnumSource(TelegramWebhookState.class)
    void inspect_classifiesTheActualUrl(TelegramWebhookState state) {
        load();
        when(secretService.isEnabled()).thenReturn(true);
        var actualUrl = switch (state) {
            case YES -> EXPECTED_URL;
            case NO -> "";
            case YES_BUT_URI_INCORRECT -> EXPECTED_URL + "/";
        };
        when(telegramService.getWebhookUrl(bot)).thenReturn(actualUrl);

        var status = service.inspect(APPLICATION_ID);

        assertThat(status.state()).isEqualTo(state);
        assertThat(status.toggleAllowed()).isTrue();
        assertThat(status.applicationVersion()).isEqualTo(3);
        verifyNoInteractions(commands);
    }

    @Test
    void inspect_whenDisabled_returnsAReadOnlyState() {
        load();
        when(telegramService.getWebhookUrl(bot)).thenReturn(EXPECTED_URL);
        assertThat(service.inspect(APPLICATION_ID).toggleAllowed()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(TelegramWebhookState.class)
    void inspect_whenBotIdIsMissing_allowsOnlyEnablingWithoutFetchingTheId(TelegramWebhookState state) {
        bot = TelegramBot.builder().token("test-token").build();
        load();
        when(secretService.isEnabled()).thenReturn(true);
        when(telegramService.getWebhookUrl(bot)).thenReturn(switch (state) {
            case NO -> "";
            case YES -> EXPECTED_URL;
            case YES_BUT_URI_INCORRECT -> "https://other.example.test/webhook";
        });
        assertThat(service.inspect(APPLICATION_ID).toggleAllowed()).isEqualTo(state == TelegramWebhookState.NO);
        verify(telegramService, never()).getBotUser(any());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void inspect_whenExpectedUrlIsMissing_doesNotQueryTelegram() {
        load();
        assertThatThrownBy(() -> service("").inspect(APPLICATION_ID))
                .hasMessage("exception.telegram.webhook.publicUrlRequired");
        verifyNoInteractions(telegramService);
    }

    @Test
    void inspect_whenApplicationIsNotTelegram_doesNotReadItsPayload() {
        application.getPrincipal().setName("https://example.test/app");
        when(repository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        assertThatThrownBy(() -> service.inspect(APPLICATION_ID))
                .hasMessage("exception.telegram.webhook.notTelegramApplication");
        verifyNoInteractions(lookup, telegramService);
    }

    @Test
    void setEnabled_whenEnabling_registersAndConfirmsExpectedUrlWithoutQueuing() {
        load();
        when(secretService.isEnabled()).thenReturn(true);
        when(secretService.secretFor(APPLICATION_ID, 100L)).thenReturn("derived-secret");
        when(telegramService.getWebhookUrl(bot)).thenReturn(EXPECTED_URL);

        assertThat(service.setEnabled(APPLICATION_ID, 3, true).state()).isEqualTo(TelegramWebhookState.YES);

        var order = inOrder(telegramService);
        order.verify(telegramService).setWebhook(bot, EXPECTED_URL, "derived-secret");
        order.verify(telegramService).getWebhookUrl(bot);
        verifyNoInteractions(commands);
        verify(repository, never()).save(any());
        verify(repository, never()).saveAndFlush(any());
        verify(telegramService, never()).getBotUser(any());
    }

    @Test
    void setEnabled_whenEnablingWithoutBotId_fetchesStoresAndUsesTheValidatedId() {
        bot = TelegramBot.builder().token("test-token").build();
        var validated = bot.withTelegramBotId(100L);
        var events = new ArrayList<String>();
        load();
        when(secretService.isEnabled()).thenReturn(true);
        when(telegramService.getBotUser(bot)).thenAnswer(invocation -> {
            events.add("getMe");
            return TelegramApiClient.TelegramUser.builder().id(100L).build();
        });
        when(mapper.writeValueAsString(validated)).thenReturn("serialized-bot");
        when(repository.saveAndFlush(application)).thenAnswer(invocation -> {
            events.add("store");
            assertThat(application.getPayload()).isEqualTo("serialized-bot");
            application.setVersion(4);
            return application;
        });
        doAnswer(invocation -> {
            events.add("commit");
            return null;
        }).when(transactionManager).commit(any());
        when(secretService.secretFor(APPLICATION_ID, 100L)).thenReturn("derived-secret");
        doAnswer(invocation -> {
            events.add("setWebhook");
            return null;
        }).when(telegramService).setWebhook(validated, EXPECTED_URL, "derived-secret");
        when(telegramService.getWebhookUrl(validated)).thenReturn(EXPECTED_URL);

        var result = service.setEnabled(APPLICATION_ID, 3, true);

        assertThat(result.state()).isEqualTo(TelegramWebhookState.YES);
        assertThat(result.applicationVersion()).isEqualTo(4);
        assertThat(events).containsExactly("getMe", "store", "commit", "setWebhook");
        verifyNoInteractions(commands);
    }

    @Test
    void setEnabled_whenBotIdLookupFails_doesNotStoreOrRegister() {
        bot = TelegramBot.builder().token("test-token").build();
        load();
        when(secretService.isEnabled()).thenReturn(true);
        when(telegramService.getBotUser(bot)).thenThrow(new IllegalArgumentException("exception.telegram.getUsername.failed"));
        assertThatThrownBy(() -> service.setEnabled(APPLICATION_ID, 3, true))
                .hasMessage("exception.telegram.getUsername.failed");
        verifyNoInteractions(mapper);
        verify(repository, never()).saveAndFlush(any());
        verify(telegramService, never()).setWebhook(any(), any(), any());
    }

    @Test
    void setEnabled_whenSerializationFails_reportsNoPayloadAndDoesNotRegister() {
        bot = TelegramBot.builder().token("test-token").build();
        load();
        when(secretService.isEnabled()).thenReturn(true);
        when(telegramService.getBotUser(bot)).thenReturn(TelegramApiClient.TelegramUser.builder().id(100L).build());
        when(mapper.writeValueAsString(bot.withTelegramBotId(100L))).thenThrow(serializationFailure);
        assertThatThrownBy(() -> service.setEnabled(APPLICATION_ID, 3, true))
                .hasMessage("Cannot store validated Telegram bot ID").hasNoCause();
        verify(repository, never()).saveAndFlush(any());
        verify(telegramService, never()).setWebhook(any(), any(), any());
    }

    @Test
    void setEnabled_whenPersistingTheIdFails_doesNotRegister() {
        bot = TelegramBot.builder().token("test-token").build();
        load();
        when(secretService.isEnabled()).thenReturn(true);
        when(telegramService.getBotUser(bot)).thenReturn(TelegramApiClient.TelegramUser.builder().id(100L).build());
        when(mapper.writeValueAsString(bot.withTelegramBotId(100L))).thenReturn("serialized-bot");
        when(repository.saveAndFlush(application)).thenThrow(new ObjectOptimisticLockingFailureException(
                IdentityApplicationEntity.class, APPLICATION_ID));
        assertThatThrownBy(() -> service.setEnabled(APPLICATION_ID, 3, true))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        verify(telegramService, never()).setWebhook(any(), any(), any());
    }

    @Test
    void setEnabled_whenDisabling_usesStrictDeletionAndConfirmsAbsence() {
        load();
        when(secretService.isEnabled()).thenReturn(true);
        when(telegramService.getWebhookUrl(bot)).thenReturn("");

        assertThat(service.setEnabled(APPLICATION_ID, 3, false).state()).isEqualTo(TelegramWebhookState.NO);

        var order = inOrder(telegramService);
        order.verify(telegramService).deleteWebhookStrict(bot);
        order.verify(telegramService).getWebhookUrl(bot);
        verify(telegramService, never()).deleteWebhook(any());
        verifyNoInteractions(commands);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void setEnabled_whenVersionIsStale_doesNotReadBotOrCallTelegram(boolean enabled) {
        when(secretService.isEnabled()).thenReturn(true);
        when(repository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        assertThatThrownBy(() -> service.setEnabled(APPLICATION_ID, 2, enabled))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        verifyNoInteractions(lookup, telegramService);
    }

    @Test
    void setEnabled_whenDisabled_doesNotLoadCredentials() {
        assertThatThrownBy(() -> service.setEnabled(APPLICATION_ID, 3, true)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(repository, lookup, telegramService);
    }

    @Test
    void setEnabled_whenBotIdIsMissing_doesNotMutateTelegram() {
        bot = TelegramBot.builder().token("test-token").build();
        load();
        when(secretService.isEnabled()).thenReturn(true);
        assertThatThrownBy(() -> service.setEnabled(APPLICATION_ID, 3, false)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(telegramService);
    }

    @Test
    void setEnabled_whenResultDiffersFromRequestedState_doesNotReportSuccess() {
        load();
        when(secretService.isEnabled()).thenReturn(true);
        when(telegramService.getWebhookUrl(bot)).thenReturn(EXPECTED_URL);
        assertThatThrownBy(() -> service.setEnabled(APPLICATION_ID, 3, false))
                .hasMessage("exception.telegram.webhook.changeNotConfirmed");
    }

    private void load() {
        when(repository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(lookup.readBot(application)).thenReturn(bot);
    }

    private TelegramWebhookRegistrationService service(String origin) {
        return new TelegramWebhookRegistrationService(telegramService, secretService, new IdentityPublicUriBuilder(origin),
                repository, mapper, lookup, commands, transactionManager, false);
    }
}
