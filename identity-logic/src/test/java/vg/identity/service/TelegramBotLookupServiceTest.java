package vg.identity.service;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.ObjectMapper;
import vg.identity.entity.IdentityApplicationEntity;
import vg.identity.entity.IdentityPrincipalEntity;
import vg.identity.model.application.TelegramBot;
import vg.identity.model.application.TelegramBotWithUri;
import vg.identity.repository.IdentityApplicationRepository;
import vg.unique.id.model.UniqueId;

import java.net.URI;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static vg.test.TestHelper.nextLong;
import static vg.test.TestHelper.nextString;
import static vg.test.TestHelper.nextUniqueId;

@ExtendWith(MockitoExtension.class)
class TelegramBotLookupServiceTest {
    @Mock private IdentityApplicationRepository applicationRepository;
    @Mock private EncryptionService encryptionService;
    @Mock private ObjectMapper objectMapper;
    private TelegramBotLookupService service;

    @BeforeEach
    void setUp() {
        service = new TelegramBotLookupService(applicationRepository, encryptionService, objectMapper);
    }

    @Test
    void telegramBotLookup_whenApplicationExists_returnsBotWithApplicationId() {
        var applicationId = nextUniqueId();
        var entity = IdentityApplicationEntity.builder().uniqueId(applicationId.getLongValue()).payload("bot-payload").build();
        var bot = TelegramBot.builder().telegramBotId(100L).token("test-token").build();
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(entity));
        when(objectMapper.readValue("bot-payload", TelegramBot.class)).thenReturn(bot);

        assertThat(service.findTelegramBot(applicationId)).isEqualTo(bot.withApplicationId(applicationId));
        assertThat(service.getTelegramBot(applicationId)).isEqualTo(bot.withApplicationId(applicationId));
    }

    @Test
    void telegramBotLookup_whenApplicationIsAbsent_findReturnsNullAndGetThrows() {
        var applicationId = nextUniqueId();
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.empty());

        assertThat(service.findTelegramBot(applicationId)).isNull();
        assertThatThrownBy(() -> service.getTelegramBot(applicationId)).isInstanceOf(EntityNotFoundException.class);
        verify(objectMapper, never()).readValue(any(String.class), eq(TelegramBot.class));
    }

    @Test
    void telegramBotLookup_whenPayloadIsMalformed_preservesExplicitFailure() {
        var applicationId = nextUniqueId();
        var entity = IdentityApplicationEntity.builder().uniqueId(applicationId.getLongValue()).payload("invalid").build();
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(entity));
        when(objectMapper.readValue("invalid", TelegramBot.class))
                .thenThrow(DatabindException.from((tools.jackson.core.JsonParser) null, "invalid payload"));

        assertThatThrownBy(() -> service.findTelegramBot(applicationId))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Cannot deserialize Telegram bot").hasNoCause();
        assertThatThrownBy(() -> service.getTelegramBot(applicationId))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Cannot deserialize Telegram bot").hasNoCause();
    }

    @Test
    void telegramBotLookup_whenPayloadIsJsonNull_rejectsMissingBot() {
        var applicationId = nextUniqueId();
        var entity = IdentityApplicationEntity.builder().uniqueId(applicationId.getLongValue()).payload("null").build();
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(entity));
        when(objectMapper.readValue("null", TelegramBot.class)).thenReturn(null);

        assertThatThrownBy(() -> service.findTelegramBot(applicationId))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Cannot deserialize Telegram bot").hasNoCause();
        assertThatThrownBy(() -> service.getTelegramBot(applicationId))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Cannot deserialize Telegram bot").hasNoCause();
    }

    @Test
    void findTelegramBotByUsername_whenBotExists_returnsBotWithUrl() throws JacksonException {
        var botUsername = nextString();
        var storedUri = "https://t.me/" + botUsername;
        var nameHash = new byte[]{7, 8, 9};
        var payload = nextString();
        var telegramBot = TelegramBot.builder().token(nextString()).build();
        var principal = applicationPrincipal(nextLong(), storedUri);
        var entity = IdentityApplicationEntity.builder()
                .uniqueId(principal.getUniqueId())
                .principal(principal)
                .payload(payload)
                .build();

        when(encryptionService.hashPrincipalName(storedUri)).thenReturn(nameHash);
        when(applicationRepository.findByPrincipal_NameHash(nameHash)).thenReturn(Optional.of(entity));
        when(objectMapper.readValue(payload, TelegramBot.class)).thenReturn(telegramBot);

        assertThat(
                service.findTelegramBotByUsername(botUsername)
        ).isEqualTo(
                TelegramBotWithUri.builder()
                        .uri(URI.create(storedUri))
                        .bot(telegramBot.withApplicationId(new UniqueId(principal.getUniqueId())))
                        .build()
        );
    }

    @Test
    void findTelegramBotByUsername_whenUsernameCasingDiffers_resolvesSameApplication() throws JacksonException {
        var normalizedUri = "https://t.me/mybot";
        var nameHash = new byte[]{7, 8, 9};
        var payload = nextString();
        var telegramBot = TelegramBot.builder().token(nextString()).build();
        var principal = applicationPrincipal(nextLong(), normalizedUri);
        var entity = IdentityApplicationEntity.builder()
                .uniqueId(principal.getUniqueId())
                .principal(principal)
                .payload(payload)
                .build();

        // Telegram usernames are case-insensitive, so "MyBot" must hash to the same key as "mybot".
        when(encryptionService.hashPrincipalName(normalizedUri)).thenReturn(nameHash);
        when(applicationRepository.findByPrincipal_NameHash(nameHash)).thenReturn(Optional.of(entity));
        when(objectMapper.readValue(payload, TelegramBot.class)).thenReturn(telegramBot);

        assertThat(
                service.findTelegramBotByUsername("MyBot")
        ).isEqualTo(
                TelegramBotWithUri.builder()
                        .uri(URI.create(normalizedUri))
                        .bot(telegramBot.withApplicationId(new UniqueId(principal.getUniqueId())))
                        .build()
        );
    }

    @Test
    void findTelegramBotByUsername_whenBotIsNotFound_returnsNull() throws JacksonException {
        var botUsername = nextString();
        var nameHash = new byte[]{7, 8, 9};

        when(encryptionService.hashPrincipalName("https://t.me/" + botUsername)).thenReturn(nameHash);
        when(applicationRepository.findByPrincipal_NameHash(nameHash)).thenReturn(Optional.empty());

        assertThat(service.findTelegramBotByUsername(botUsername)).isNull();
        verify(objectMapper, never()).readValue(any(String.class), eq(TelegramBot.class));
    }

    @Test
    void readBot_whenApplicationIsAlreadyLoaded_attachesIdWithoutAnotherQuery() {
        var applicationId = nextUniqueId();
        var application = IdentityApplicationEntity.builder()
                .uniqueId(applicationId.getLongValue()).payload("bot-payload").build();
        var bot = TelegramBot.builder().telegramBotId(100L).token("test-token").build();
        when(objectMapper.readValue("bot-payload", TelegramBot.class)).thenReturn(bot);

        assertThat(service.readBot(application)).isEqualTo(bot.withApplicationId(applicationId));
        verifyNoInteractions(applicationRepository, encryptionService);
    }

    private static IdentityPrincipalEntity applicationPrincipal(long id, String uri) {
        return IdentityPrincipalEntity.builder().uniqueId(id).name(uri).build();
    }
}
