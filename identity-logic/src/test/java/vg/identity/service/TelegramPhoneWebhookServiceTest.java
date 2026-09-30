package vg.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vg.identity.entity.IdentityUserEntity;
import vg.identity.model.TelegramWebhookUpdate;
import vg.identity.model.application.TelegramBot;
import vg.unique.id.model.UniqueId;

import java.time.Instant;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelegramPhoneWebhookServiceTest {
    private static final UniqueId APPLICATION_ID = new UniqueId(11L);
    @Mock private TelegramWebhookSecretService secretService;
    @Mock private TelegramBotLookupService telegramBotLookupService;
    @Mock private IdentityUserChannelService channelService;
    @Mock private IdentityApplicationUserService applicationUserService;
    @Mock private PhoneNumberNormalizer phoneNumberNormalizer;
    private TelegramPhoneWebhookService service;

    @BeforeEach
    void setUp() {
        service = new TelegramPhoneWebhookService(secretService, telegramBotLookupService, channelService,
                applicationUserService, phoneNumberNormalizer);
    }

    @Test
    void accept_whenSelfContactIsAuthenticated_updatesPhoneAndMembership() throws Exception {
        authenticate();
        var user = consentedUser();
        when(phoneNumberNormalizer.normalize("+14155552671")).thenReturn("+14155552671");
        when(channelService.findUserByTelegramId(42L)).thenReturn(user);
        when(channelService.recordTelegramPhone(42L, "+14155552671")).thenReturn(true);
        service.accept(APPLICATION_ID, "secret", contact("private", 42L));
        verify(applicationUserService).enableTelegramPhoneDisclosure(APPLICATION_ID, 77L);
    }

    @Test
    void accept_whenDisabled_hasNoEffect() {
        service.accept(APPLICATION_ID, "secret", contact("private", 42L));
        verifyNoInteractions(telegramBotLookupService, channelService);
    }

    @Test
    void accept_whenSecretBelongsToPreviousBot_hasNoEffect() throws Exception {
        loadBot(101L);
        service.accept(APPLICATION_ID, "previous-bot-secret", contact("private", 42L));
        verify(secretService).matches(APPLICATION_ID, 101L, "previous-bot-secret");
        verifyNoInteractions(channelService, applicationUserService, phoneNumberNormalizer);
    }

    @Test
    void accept_whenStoredBotHasNoId_hasNoEffect() throws Exception {
        loadBot(null);
        service.accept(APPLICATION_ID, "secret", contact("private", 42L));
        verifyNoInteractions(channelService, applicationUserService, phoneNumberNormalizer);
    }

    @Test
    void accept_whenApplicationIsUnknown_hasNoEffect() {
        when(secretService.isEnabled()).thenReturn(true);
        service.accept(APPLICATION_ID, "secret", contact("private", 42L));
        verifyNoInteractions(channelService, applicationUserService, phoneNumberNormalizer);
    }

    @Test
    void accept_whenBotPayloadIsMalformed_hasNoEffect() {
        when(secretService.isEnabled()).thenReturn(true);
        when(telegramBotLookupService.findTelegramBot(APPLICATION_ID))
                .thenThrow(new IllegalArgumentException("Cannot deserialize Telegram bot"));
        service.accept(APPLICATION_ID, "secret", contact("private", 42L));
        verifyNoInteractions(channelService, applicationUserService, phoneNumberNormalizer);
    }

    @Test
    void accept_whenContactIsForAnotherUser_hasNoEffect() throws Exception {
        authenticate();
        service.accept(APPLICATION_ID, "secret", contact("private", 43L));
        verifyNoInteractions(channelService, applicationUserService, phoneNumberNormalizer);
    }

    @Test
    void accept_whenNotPrivate_hasNoEffect() throws Exception {
        authenticate();
        service.accept(APPLICATION_ID, "secret", contact("group", 42L));
        verifyNoInteractions(channelService, applicationUserService, phoneNumberNormalizer);
    }

    @Test
    void accept_whenContactHasNoUserId_hasNoEffect() throws Exception {
        authenticate();
        service.accept(APPLICATION_ID, "secret", contact("private", null));
        verifyNoInteractions(channelService, applicationUserService, phoneNumberNormalizer);
    }

    @Test
    void accept_whenTelegramIdentityIsNotEstablished_hasNoEffect() throws Exception {
        authenticate();
        when(phoneNumberNormalizer.normalize("+14155552671")).thenReturn("+14155552671");
        service.accept(APPLICATION_ID, "secret", contact("private", 42L));
        verify(channelService, never()).recordTelegramPhone(anyLong(), any());
        verifyNoInteractions(applicationUserService);
    }

    @Test
    void accept_whenConsentIsMissing_hasNoEffect() throws Exception {
        authenticate();
        when(phoneNumberNormalizer.normalize("+14155552671")).thenReturn("+14155552671");
        when(channelService.findUserByTelegramId(42L)).thenReturn(IdentityUserEntity.builder().uniqueId(77L).build());
        service.accept(APPLICATION_ID, "secret", contact("private", 42L));
        verify(channelService, never()).recordTelegramPhone(anyLong(), any());
        verifyNoInteractions(applicationUserService);
    }

    @Test
    void accept_whenPhoneCannotBeRecorded_doesNotEnableDisclosure() throws Exception {
        authenticate();
        when(phoneNumberNormalizer.normalize("+14155552671")).thenReturn("+14155552671");
        when(channelService.findUserByTelegramId(42L)).thenReturn(consentedUser());
        service.accept(APPLICATION_ID, "secret", contact("private", 42L));
        verifyNoInteractions(applicationUserService);
    }

    @Test
    void accept_whenPhoneCannotBeNormalized_hasNoEffect() throws Exception {
        authenticate();
        service.accept(APPLICATION_ID, "secret", contact("private", 42L));
        verifyNoInteractions(channelService, applicationUserService);
    }

    private void authenticate() throws Exception {
        loadBot(100L);
        when(secretService.matches(APPLICATION_ID, 100L, "secret")).thenReturn(true);
    }

    private void loadBot(Long botId) throws Exception {
        when(secretService.isEnabled()).thenReturn(true);
        when(telegramBotLookupService.findTelegramBot(APPLICATION_ID))
                .thenReturn(TelegramBot.builder().telegramBotId(botId).token("token").build());
    }

    private static IdentityUserEntity consentedUser() {
        return IdentityUserEntity.builder().uniqueId(77L).consentToKeepPersonalDataAt(Instant.parse("2026-09-30T10:00:00Z")).build();
    }

    private static TelegramWebhookUpdate contact(String chatType, Long contactUserId) {
        return new TelegramWebhookUpdate(new TelegramWebhookUpdate.TelegramWebhookMessage(
                new TelegramWebhookUpdate.TelegramWebhookUser(42L), new TelegramWebhookUpdate.TelegramWebhookChat(chatType),
                new TelegramWebhookUpdate.TelegramWebhookContact("+14155552671", contactUserId)));
    }
}
