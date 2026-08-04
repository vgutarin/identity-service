package vg.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import vg.identity.IdentityActionTokenProperties;
import vg.identity.entity.IdentityActionTokenEntity;
import vg.identity.entity.IdentityPrincipalEntity;
import vg.identity.entity.IdentityUserChannelEntity;
import vg.identity.entity.IdentityUserEntity;
import vg.identity.model.EmailMessage;
import vg.identity.model.IdentityActionType;
import vg.identity.model.IdentityChannelType;
import vg.identity.model.IdentityPrincipalType;
import vg.identity.model.application.TelegramBot;
import vg.identity.model.application.TelegramBotToConfirm;
import vg.identity.model.application.TelegramBotWithUri;
import vg.identity.model.user.channel.IdentityUserChannelEmail;
import vg.identity.repository.IdentityActionTokenRepository;
import vg.identity.repository.IdentityPrincipalRepository;
import vg.identity.repository.IdentityUserChannelRepository;
import vg.unique.id.model.UniqueId;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityActionTokenServiceTest {
    private static final String SECRET = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final byte[] RAW_SECRET = Base64.getUrlDecoder().decode(SECRET);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-07T18:00:00Z"), ZoneOffset.UTC);

    @Mock private IdentityActionTokenRepository actionTokenRepository;
    @Mock private IdentityPrincipalRepository principalRepository;
    @Mock private IdentityUserChannelRepository channelRepository;
    @Mock private IdentityCommandService commandService;
    @Mock private IdentityApplicationService applicationService;
    @Mock private ConfirmEmailMailFactory confirmEmailMailFactory;
    @Mock private ObjectMapper objectMapper;

    private IdentityActionTokenProperties properties;
    private IdentityActionTokenService service;

    @BeforeEach
    void setUp() {
        properties = new IdentityActionTokenProperties();
        properties.setVerifyEmailBaseUrl("https://example.com/verify/");
        properties.setExpiresIn(Duration.ofHours(2));
        properties.setRequestCooldown(Duration.ofMinutes(5));
        service = new IdentityActionTokenService(
                actionTokenRepository,
                principalRepository,
                channelRepository,
                commandService,
                properties,
                new IdentityActionLinkBuilderDefault(properties),
                applicationService,
                confirmEmailMailFactory,
                objectMapper,
                "Identityvgbot",
                clock
        );
    }

    @Test
    void confirm_issuesOpaqueActionKeyAndPersistsOnlyItsHash() {
        var channel = emailChannel(7L, "john@example.com");
        var entity = new AtomicReference<IdentityActionTokenEntity>();
        when(actionTokenRepository.existsByActionTypeAndIdentityUserChannelUniqueIdAndCreatedAtGreaterThanEqual(
                IdentityActionType.CONFIRM_EMAIL, 7L, clock.instant().minus(Duration.ofMinutes(5))
        )).thenReturn(false);
        when(channelRepository.getReferenceById(7L)).thenReturn(IdentityUserChannelEntity.builder().uniqueId(7L).build());
        when(principalRepository.getReferenceById(17L)).thenReturn(IdentityPrincipalEntity.builder().uniqueId(17L).build());
        when(actionTokenRepository.save(any(IdentityActionTokenEntity.class))).thenAnswer(invocation -> {
            var saved = invocation.getArgument(0, IdentityActionTokenEntity.class);
            saved.setId(7L);
            entity.set(saved);
            return saved;
        });
        var email = EmailMessage.builder().build();
        when(confirmEmailMailFactory.create(any(), any(), any())).thenReturn(email);

        service.confirm(channel);

        assertThat(entity.get().getId()).isEqualTo(7L);
        assertThat(entity.get().getSecretHash()).hasSize(32);
        assertThat(entity.get().getSecretHash()).isNotEqualTo(new byte[32]);
        assertThat(entity.get().getActionType()).isEqualTo(IdentityActionType.CONFIRM_EMAIL);
        assertThat(entity.get().getPrincipalType()).isEqualTo(IdentityPrincipalType.USER);
        assertThat(entity.get().getIdentityUserChannel().getUniqueId()).isEqualTo(7L);
        assertThat(entity.get().getPayload()).isNull();
        assertThat(entity.get().getCreatedAt()).isEqualTo(clock.instant());
        assertThat(entity.get().getExpireAt()).isEqualTo(clock.instant().plus(Duration.ofHours(2)));
        var url = ArgumentCaptor.forClass(URI.class);
        verify(confirmEmailMailFactory).create(eq("john@example.com"), url.capture(), isNull());
        assertThat(url.getValue().toString()).matches("https://example\\.com/verify/7_[A-Za-z0-9_-]{43}");
        verify(commandService).enqueue(email);
    }

    @Test
    void confirm_whenWithinCooldown_doesNotIssueAction() {
        var channel = emailChannel(7L, "john@example.com");
        when(actionTokenRepository.existsByActionTypeAndIdentityUserChannelUniqueIdAndCreatedAtGreaterThanEqual(
                IdentityActionType.CONFIRM_EMAIL, 7L, clock.instant().minus(Duration.ofMinutes(5))
        )).thenReturn(true);

        service.confirm(channel);

        verify(channelRepository, never()).getReferenceById(7L);
        verify(principalRepository, never()).getReferenceById(17L);
        verify(actionTokenRepository, never()).save(any());
        verify(commandService, never()).enqueue(any());
    }

    @Test
    void confirm_whenRequiredChannelDataIsMissing_failsFast() {
        assertThatThrownBy(() -> service.confirm(emailChannel(null, "john@example.com")))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("channel uniqueId is required");
        assertThatThrownBy(() -> service.confirm(emailChannel(7L, null)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("channel email is required");
    }

    @Test
    void confirm_whenChannelIdentityUserUniqueIdIsMissing_createsPendingVerification() {
        var channel = emailChannel(7L, "john@example.com");
        channel.setIdentityUserUniqueId(null);
        when(actionTokenRepository.save(any(IdentityActionTokenEntity.class))).thenAnswer(invocation -> {
            var saved = invocation.getArgument(0, IdentityActionTokenEntity.class);
            saved.setId(7L);
            return saved;
        });

        service.confirm(channel);

        verify(principalRepository, never()).getReferenceById(any());
        verify(actionTokenRepository).save(any(IdentityActionTokenEntity.class));
    }

    @Test
    void findConfirmEmailActionInfo_whenVerificationDoesNotExist_returnsNull() {
        when(actionTokenRepository.findById(7L)).thenReturn(Optional.empty());

        assertThat(service.findConfirmEmailActionInfo(actionKey(7L))).isNull();
    }

    @Test
    void findConfirmEmailActionInfo_whenVerificationIsExpired_returnsNull() {
        var verification = token(7L, IdentityActionType.CONFIRM_EMAIL);
        verification.setExpireAt(clock.instant());
        when(actionTokenRepository.findById(7L)).thenReturn(Optional.of(verification));

        assertThat(service.findConfirmEmailActionInfo(actionKey(7L))).isNull();
    }

    @Test
    void findConfirmEmailActionInfo_whenChannelHasUser_returnsUserUniqueId() {
        var token = token(7L, IdentityActionType.CONFIRM_EMAIL);
        token.setIdentityUserChannel(IdentityUserChannelEntity.builder().identityUser(IdentityUserEntity.builder().uniqueId(17L).build()).build());
        when(actionTokenRepository.findById(7L)).thenReturn(Optional.of(token));

        var result = service.findConfirmEmailActionInfo(actionKey(7L));

        assertThat(result.actionKey()).isEqualTo(actionKey(7L));
        assertThat(result.userUniqueId()).isEqualTo(new UniqueId(17L));
        assertThat(result.personalInformationConsentGiven()).isFalse();
    }

    @Test
    void findConfirmEmailActionInfo_whenPrincipalHasPersonalInformationConsent_returnsConfirmEmailInfoWithConsentTrue() {
        var user = IdentityUserEntity.builder()
                .uniqueId(17L)
                .consentToKeepPersonalDataAt(clock.instant().minus(Duration.ofDays(1)))
                .build();
        var token = token(7L, IdentityActionType.CONFIRM_EMAIL);
        token.setIdentityUserChannel(IdentityUserChannelEntity.builder().identityUser(user).build());
        when(actionTokenRepository.findById(7L)).thenReturn(Optional.of(token));

        var result = service.findConfirmEmailActionInfo(actionKey(7L));

        assertThat(result.personalInformationConsentGiven()).isTrue();
    }

    @Test
    void findConfirmEmailActionInfo_whenTokenTypeIsNotConfirmEmail_returnsNull() {
        when(actionTokenRepository.findById(7L)).thenReturn(Optional.of(token(7L, IdentityActionType.BIND_TELEGRAM)));

        assertThat(service.findConfirmEmailActionInfo(actionKey(7L))).isNull();
    }

    @Test
    void findConfirmEmailActionInfo_whenSecretIsWrong_returnsNull() {
        when(actionTokenRepository.findById(7L)).thenReturn(Optional.of(token(7L, IdentityActionType.CONFIRM_EMAIL)));

        assertThat(service.findConfirmEmailActionInfo("7_BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")).isNull();
    }

    @Test
    void findConfirmEmailActionInfo_whenKeyIsMalformed_returnsNullWithoutLookup() {
        assertThat(service.findConfirmEmailActionInfo("not-an-action-key")).isNull();

        verify(actionTokenRepository, never()).findById(any());
    }

    @Test
    void findConfirmEmailActionForUpdate_verifiesSecretAfterLocking() {
        var token = token(7L, IdentityActionType.CONFIRM_EMAIL);
        token.setIdentityUserChannel(IdentityUserChannelEntity.builder().channelType(IdentityChannelType.EMAIL).build());
        when(actionTokenRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(token));

        assertThat(service.findConfirmEmailActionForUpdate(actionKey(7L))).isSameAs(token);
        verify(actionTokenRepository).findByIdForUpdate(7L);
    }

    @Test
    void findConfirmEmailActionForUpdate_whenTokenTypeIsNotConfirmEmail_returnsNull() {
        var token = token(7L, IdentityActionType.BIND_TELEGRAM);
        token.setIdentityUserChannel(emailChannelEntity());
        when(actionTokenRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(token));

        assertThat(service.findConfirmEmailActionForUpdate(actionKey(7L))).isNull();
    }

    @Test
    void findConfirmEmailActionForUpdate_whenVerificationDoesNotExist_returnsNull() {
        when(actionTokenRepository.findByIdForUpdate(7L)).thenReturn(Optional.empty());

        assertThat(service.findConfirmEmailActionForUpdate(actionKey(7L))).isNull();
    }

    @Test
    void findConfirmEmailActionForUpdate_whenVerificationIsExpired_returnsNull() {
        var token = token(7L, IdentityActionType.CONFIRM_EMAIL);
        token.setIdentityUserChannel(emailChannelEntity());
        token.setExpireAt(clock.instant());
        when(actionTokenRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(token));

        assertThat(service.findConfirmEmailActionForUpdate(actionKey(7L))).isNull();
    }

    @Test
    void findBindTelegramActionInfo_whenKeyIsValid_returnsInternalTokenId() throws Exception {
        var principal = IdentityPrincipalEntity.builder().uniqueId(17L).type(IdentityPrincipalType.USER).build();
        var token = token(7L, IdentityActionType.BIND_TELEGRAM);
        token.setPrincipalType(IdentityPrincipalType.USER);
        token.setPrincipal(principal);
        token.setPayload("{\"botUsername\":\"Identityvgbot\"}");
        when(actionTokenRepository.findById(7L)).thenReturn(Optional.of(token));
        when(objectMapper.readValue(token.getPayload(), TelegramBotToConfirm.class))
                .thenReturn(new TelegramBotToConfirm("Identityvgbot"));
        when(applicationService.findTelegramBotByUsername("Identityvgbot")).thenReturn(telegramBot());

        var result = service.findBindTelegramActionInfo(actionKey(7L));

        assertThat(result.tokenId()).isEqualTo(7L);
        assertThat(result.telegramBot()).isEqualTo(telegramBot().bot());
        assertThat(result.principal()).isSameAs(principal);
    }

    @Test
    void findBindTelegramActionInfo_whenTokenIsNotBindTelegram_returnsNull() {
        when(actionTokenRepository.findById(7L)).thenReturn(Optional.of(token(7L, IdentityActionType.CONFIRM_EMAIL)));

        assertThat(service.findBindTelegramActionInfo(actionKey(7L))).isNull();
    }

    @Test
    void createBindTelegramUrlIfTelegramIsMissing_whenPrincipalHasTelegramChannel_returnsNull() {
        var user = IdentityUserEntity.builder().uniqueId(17L).build();
        user.setPrincipal(IdentityPrincipalEntity.builder().uniqueId(17L).build());
        when(applicationService.findTelegramBotByUsername("Identityvgbot")).thenReturn(telegramBot());
        when(channelRepository.existsByIdentityUserUniqueIdAndChannelType(17L, IdentityChannelType.TELEGRAM_USER))
                .thenReturn(true);

        assertThat(service.createBindTelegramUrlIfTelegramIsMissing(user)).isNull();

        verify(actionTokenRepository, never()).save(any(IdentityActionTokenEntity.class));
    }

    @Test
    void createBindTelegramUrlIfTelegramIsMissing_whenPrincipalHasNoTelegramChannel_createsActionAndReturnsUrl() throws Exception {
        var user = IdentityUserEntity.builder().uniqueId(17L).build();
        user.setPrincipal(IdentityPrincipalEntity.builder().uniqueId(17L).build());
        when(applicationService.findTelegramBotByUsername("Identityvgbot")).thenReturn(telegramBot());
        when(channelRepository.existsByIdentityUserUniqueIdAndChannelType(17L, IdentityChannelType.TELEGRAM_USER)).thenReturn(false);
        when(objectMapper.writeValueAsString(new TelegramBotToConfirm("Identityvgbot"))).thenReturn("{}");
        var savedAction = new AtomicReference<IdentityActionTokenEntity>();
        when(actionTokenRepository.save(any(IdentityActionTokenEntity.class))).thenAnswer(invocation -> {
            var saved = invocation.getArgument(0, IdentityActionTokenEntity.class);
            saved.setId(7L);
            savedAction.set(saved);
            return saved;
        });

        var url = service.createBindTelegramUrlIfTelegramIsMissing(user);

        assertThat(savedAction.get().getActionType()).isEqualTo(IdentityActionType.BIND_TELEGRAM);
        assertThat(savedAction.get().getPrincipalType()).isEqualTo(IdentityPrincipalType.USER);
        assertThat(savedAction.get().getPrincipal()).isSameAs(user.getPrincipal());
        assertThat(savedAction.get().getIdentityUserChannel()).isNull();
        assertThat(savedAction.get().getPayload()).isEqualTo("{}");
        assertThat(savedAction.get().getCreatedAt()).isEqualTo(clock.instant());
        assertThat(savedAction.get().getExpireAt()).isEqualTo(clock.instant().plus(Duration.ofHours(2)));
        assertThat(savedAction.get().getSecretHash()).hasSize(32);
        assertThat(url.toString()).matches("https://t\\.me/identityvgbot\\?startapp=7_[A-Za-z0-9_-]{43}");
    }

    private IdentityActionTokenEntity token(long id, IdentityActionType type) {
        return IdentityActionTokenEntity.builder()
                .id(id)
                .secretHash(OpaqueKey.sha256(RAW_SECRET))
                .actionType(type)
                .createdAt(clock.instant())
                .expireAt(clock.instant().plus(Duration.ofHours(2)))
                .build();
    }

    private static String actionKey(long id) {
        return id + "_" + SECRET;
    }

    private static IdentityUserChannelEmail emailChannel(Long uniqueId, String email) {
        var channel = new IdentityUserChannelEmail();
        if (uniqueId != null) channel.setUniqueId(new UniqueId(uniqueId));
        channel.setIdentityUserUniqueId(new UniqueId(17L));
        channel.setChannelType(IdentityChannelType.EMAIL);
        channel.setChannelUserId(email);
        return channel;
    }

    private static IdentityUserChannelEntity emailChannelEntity() {
        return IdentityUserChannelEntity.builder()
                .uniqueId(7L)
                .channelType(IdentityChannelType.EMAIL)
                .channelUserId("john@example.com")
                .build();
    }

    private static TelegramBotWithUri telegramBot() {
        return new TelegramBotWithUri(URI.create("https://t.me/identityvgbot"), TelegramBot.builder().token("token").build());
    }
}
