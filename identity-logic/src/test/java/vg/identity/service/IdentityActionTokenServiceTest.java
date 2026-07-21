package vg.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import vg.identity.IdentityActionTokenProperties;
import vg.identity.entity.IdentityActionTokenEntity;
import vg.identity.entity.IdentityPrincipalEntity;
import vg.identity.entity.IdentityUserChannelEntity;
import vg.identity.entity.IdentityUserEntity;
import vg.identity.model.IdentityAction;
import vg.identity.model.EmailMessage;
import vg.identity.model.IdentityActionType;
import vg.identity.model.IdentityChannelType;
import vg.identity.model.IdentityPrincipalType;
import vg.identity.model.application.TelegramBot;
import vg.identity.model.application.TelegramBotToConfirm;
import vg.identity.model.application.TelegramBotWithUrl;
import vg.identity.model.user.channel.IdentityUserChannelEmail;
import vg.identity.repository.IdentityActionTokenRepository;
import vg.identity.repository.IdentityPrincipalRepository;
import vg.identity.repository.IdentityUserChannelRepository;
import vg.unique.id.model.UniqueId;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityActionTokenServiceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-07T18:00:00Z"), ZoneOffset.UTC);

    @Mock
    private IdentityActionTokenRepository actionTokenRepository;
    @Mock
    private IdentityPrincipalRepository principalRepository;
    @Mock
    private IdentityUserChannelRepository channelRepository;
    @Mock
    private IdentityCommandService commandService;
    @Mock
    private IdentityApplicationService applicationService;
    @Mock
    private ObjectMapper objectMapper;

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
                applicationService,
                objectMapper,
                "Identityvgbot",
                clock
        );
    }

    @Test
    void confirm_whenEmailChannelProvided_savesVerificationAndEnqueuesEmail() {
        var channel = emailChannel(7L, "john@example.com");
        var principal = IdentityPrincipalEntity.builder()
                .uniqueId(17L)
                .type(IdentityPrincipalType.USER)
                .build();
        var channelEntity = IdentityUserChannelEntity.builder()
                .uniqueId(7L)
                .channelType(IdentityChannelType.EMAIL)
                .channelUserId("john@example.com")
                .build();
        var savedVerification = new AtomicReference<IdentityActionTokenEntity>();
        when(actionTokenRepository.existsByActionTypeAndIdentityUserChannelUniqueIdAndCreatedAtGreaterThanEqual(
                IdentityActionType.CONFIRM_EMAIL,
                7L,
                clock.instant().minus(Duration.ofMinutes(5))
        )).thenReturn(false);
        when(principalRepository.getReferenceById(17L)).thenReturn(principal);
        when(channelRepository.getReferenceById(7L)).thenReturn(channelEntity);
        when(actionTokenRepository.save(any(IdentityActionTokenEntity.class)))
                .thenAnswer(invocation -> {
                    var entity = invocation.getArgument(0, IdentityActionTokenEntity.class);
                    savedVerification.set(entity);
                    return entity;
                });

        service.confirm(channel);

        var verification = savedVerification.get();
        assertThat(verification.getId()).isNotNull();
        assertThat(verification.getActionType()).isEqualTo(IdentityActionType.CONFIRM_EMAIL);
        assertThat(verification.getPrincipalType()).isEqualTo(IdentityPrincipalType.USER);
        assertThat(verification.getPrincipal()).isSameAs(principal);
        assertThat(verification.getIdentityUserChannel()).isSameAs(channelEntity);
        assertThat(verification.getPayload()).isNull();
        assertThat(verification.getCreatedAt()).isEqualTo(clock.instant());
        assertThat(verification.getExpireAt()).isEqualTo(clock.instant().plus(Duration.ofHours(2)));

        var expectedEmail = EmailMessage.builder()
                .to(java.util.List.of("john@example.com"))
                .subject("Verify your email")
                .body("https://example.com/verify/" + verification.getId())
                .build();
        verify(commandService).enqueue(expectedEmail);
    }

    @Test
    void confirm_whenVerificationWasRequestedInsideCooldown_doesNotSaveVerificationAndDoesNotEnqueueEmail() {
        var channel = emailChannel(7L, "john@example.com");
        when(actionTokenRepository.existsByActionTypeAndIdentityUserChannelUniqueIdAndCreatedAtGreaterThanEqual(
                IdentityActionType.CONFIRM_EMAIL,
                7L,
                clock.instant().minus(Duration.ofMinutes(5))
        )).thenReturn(true);

        service.confirm(channel);

        verify(channelRepository, never()).getReferenceById(7L);
        verify(principalRepository, never()).getReferenceById(17L);
        verify(actionTokenRepository, never()).save(any(IdentityActionTokenEntity.class));
        verify(commandService, never()).enqueue(any(EmailMessage.class));
    }

    @Test
    void confirm_whenChannelUniqueIdIsMissing_throwsNullPointerException() {
        var channel = emailChannel(null, "john@example.com");

        assertThatThrownBy(() -> service.confirm(channel))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("channel uniqueId is required");
    }

    @Test
    void confirm_whenChannelIdentityUserUniqueIdIsMissing_throwsNullPointerException() {
        var channel = emailChannel(7L, "john@example.com");
        channel.setIdentityUserUniqueId(null);

        assertThatThrownBy(() -> service.confirm(channel))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("channel identityUserUniqueId is required");
    }

    @Test
    void confirm_whenChannelEmailIsMissing_throwsNullPointerException() {
        var channel = emailChannel(7L, null);

        assertThatThrownBy(() -> service.confirm(channel))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("channel email is required");
    }

    @Test
    void findConfirmEmailActionInfo_whenVerificationDoesNotExist_returnsNull() {
        var id = UUID.randomUUID();
        when(actionTokenRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(service.findConfirmEmailActionInfo(id)).isNull();
    }

    @Test
    void findConfirmEmailActionInfo_whenVerificationIsExpired_returnsNull() {
        var id = UUID.randomUUID();
        var verification = IdentityActionTokenEntity.builder()
                .id(id)
                .expireAt(clock.instant())
                .build();
        when(actionTokenRepository.findById(id)).thenReturn(Optional.of(verification));

        assertThat(service.findConfirmEmailActionInfo(id)).isNull();
    }

    @Test
    void findConfirmEmailActionInfo_whenPrincipalHasNoPersonalInformationConsent_returnsConfirmEmailInfoWithConsentFalse() {
        var id = UUID.randomUUID();
        var principal = IdentityUserEntity.builder()
                .uniqueId(17L)
                .build();
        var channelEntity = IdentityUserChannelEntity.builder()
                .uniqueId(7L)
                .identityUser(principal)
                .build();
        var verification = IdentityActionTokenEntity.builder()
                .id(id)
                .actionType(IdentityActionType.CONFIRM_EMAIL)
                .identityUserChannel(channelEntity)
                .expireAt(clock.instant().plus(Duration.ofHours(2)))
                .build();
        when(actionTokenRepository.findById(id)).thenReturn(Optional.of(verification));

        var result = service.findConfirmEmailActionInfo(id);

        assertThat(result.id()).isEqualTo(id);
        assertThat(result.personalInformationConsentGiven()).isFalse();
    }

    @Test
    void findConfirmEmailActionInfo_whenTokenTypeIsNotConfirmEmail_returnsNull() {
        var id = UUID.randomUUID();
        var token = IdentityActionTokenEntity.builder()
                .id(id)
                .actionType(IdentityActionType.BIND_TELEGRAM)
                .expireAt(clock.instant().plus(Duration.ofHours(2)))
                .build();
        when(actionTokenRepository.findById(id)).thenReturn(Optional.of(token));

        assertThat(service.findConfirmEmailActionInfo(id)).isNull();
    }

    @Test
    void findConfirmEmailActionInfo_whenPrincipalHasPersonalInformationConsent_returnsConfirmEmailInfoWithConsentTrue() {
        var id = UUID.randomUUID();
        var principal = IdentityUserEntity.builder()
                .uniqueId(17L)
                .consentToKeepPersonalDataAt(clock.instant().minus(Duration.ofDays(1)))
                .build();
        var channelEntity = IdentityUserChannelEntity.builder()
                .uniqueId(7L)
                .identityUser(principal)
                .build();
        var verification = IdentityActionTokenEntity.builder()
                .id(id)
                .actionType(IdentityActionType.CONFIRM_EMAIL)
                .identityUserChannel(channelEntity)
                .expireAt(clock.instant().plus(Duration.ofHours(2)))
                .build();
        when(actionTokenRepository.findById(id)).thenReturn(Optional.of(verification));

        var result = service.findConfirmEmailActionInfo(id);

        assertThat(result.id()).isEqualTo(id);
        assertThat(result.personalInformationConsentGiven()).isTrue();
    }

    @Test
    void confirmEmail_whenVerificationExistsAndIsNotExpired_setsChannelVerifiedAtConsentsPrincipalAndReturnsTrue() {
        var id = UUID.randomUUID();
        var principal = IdentityUserEntity.builder()
                .uniqueId(17L)
                .build();
        var channelEntity = IdentityUserChannelEntity.builder()
                .uniqueId(7L)
                .identityUser(principal)
                .channelType(IdentityChannelType.EMAIL)
                .channelUserId("john@example.com")
                .build();
        var verification = IdentityActionTokenEntity.builder()
                .id(id)
                .actionType(IdentityActionType.CONFIRM_EMAIL)
                .identityUserChannel(channelEntity)
                .createdAt(clock.instant().minus(Duration.ofMinutes(1)))
                .expireAt(clock.instant().plus(Duration.ofHours(2)))
                .build();
        when(actionTokenRepository.findById(id)).thenReturn(Optional.of(verification));
        when(channelRepository.save(channelEntity)).thenReturn(channelEntity);

        assertThat(service.confirmEmail(id).success()).isTrue();

        assertThat(channelEntity.getVerifiedAt()).isEqualTo(clock.instant());
        assertThat(principal.getConsentToKeepPersonalDataAt()).isEqualTo(clock.instant());
        verify(channelRepository).save(channelEntity);
        verify(channelRepository).flush();
        verify(actionTokenRepository).deleteById(id);
    }

    @Test
    void confirmEmail_whenTelegramBotExistsAndPrincipalHasTelegramChannel_returnsNullBindTelegramUrl() {
        var id = UUID.randomUUID();
        var user = IdentityUserEntity.builder()
                .uniqueId(17L)
                .build();
        var principal = IdentityPrincipalEntity.builder()
                .uniqueId(17L)
                .type(IdentityPrincipalType.USER)
                .build();
        var channelEntity = IdentityUserChannelEntity.builder()
                .uniqueId(7L)
                .identityUser(user)
                .channelType(IdentityChannelType.EMAIL)
                .channelUserId("john@example.com")
                .build();
        var verification = IdentityActionTokenEntity.builder()
                .id(id)
                .actionType(IdentityActionType.CONFIRM_EMAIL)
                .principal(principal)
                .principalType(IdentityPrincipalType.USER)
                .identityUserChannel(channelEntity)
                .createdAt(clock.instant().minus(Duration.ofMinutes(1)))
                .expireAt(clock.instant().plus(Duration.ofHours(2)))
                .build();
        when(actionTokenRepository.findById(id)).thenReturn(Optional.of(verification));
        when(channelRepository.save(channelEntity)).thenReturn(channelEntity);
        when(applicationService.findTelegramBotByUsername("Identityvgbot")).thenReturn(telegramBot());
        when(channelRepository.existsByIdentityUserUniqueIdAndChannelType(17L, IdentityChannelType.TELEGRAM_USER))
                .thenReturn(true);

        var result = service.confirmEmail(id);

        assertThat(result.success()).isTrue();
        assertThat(result.bindTelegramUrl()).isNull();
        verify(actionTokenRepository, never()).save(any(IdentityActionTokenEntity.class));
    }

    @Test
    void confirmEmail_whenTelegramBotExistsAndPrincipalHasNoTelegramChannel_createsBindTelegramActionAndReturnsBindTelegramUrl() throws Exception {
        var id = UUID.randomUUID();
        var user = IdentityUserEntity.builder()
                .uniqueId(17L)
                .build();
        var principal = IdentityPrincipalEntity.builder()
                .uniqueId(17L)
                .type(IdentityPrincipalType.USER)
                .build();
        var channelEntity = IdentityUserChannelEntity.builder()
                .uniqueId(7L)
                .identityUser(user)
                .channelType(IdentityChannelType.EMAIL)
                .channelUserId("john@example.com")
                .build();
        var verification = IdentityActionTokenEntity.builder()
                .id(id)
                .actionType(IdentityActionType.CONFIRM_EMAIL)
                .principal(principal)
                .principalType(IdentityPrincipalType.USER)
                .identityUserChannel(channelEntity)
                .createdAt(clock.instant().minus(Duration.ofMinutes(1)))
                .expireAt(clock.instant().plus(Duration.ofHours(2)))
                .build();
        when(actionTokenRepository.findById(id)).thenReturn(Optional.of(verification));
        when(channelRepository.save(channelEntity)).thenReturn(channelEntity);
        when(applicationService.findTelegramBotByUsername("Identityvgbot")).thenReturn(telegramBot());
        when(channelRepository.existsByIdentityUserUniqueIdAndChannelType(17L, IdentityChannelType.TELEGRAM_USER))
                .thenReturn(false);
        when(objectMapper.writeValueAsString(new TelegramBotToConfirm("Identityvgbot")))
                .thenReturn("{\"botUsername\":\"Identityvgbot\"}");
        var savedBindTelegramAction = new AtomicReference<IdentityActionTokenEntity>();
        when(actionTokenRepository.save(any(IdentityActionTokenEntity.class)))
                .thenAnswer(invocation -> {
                    var entity = invocation.getArgument(0, IdentityActionTokenEntity.class);
                    savedBindTelegramAction.set(entity);
                    return entity;
                });

        var result = service.confirmEmail(id);
        var bindTelegramAction = savedBindTelegramAction.get();

        assertThat(result.success()).isTrue();
        assertThat(bindTelegramAction.getId()).isNotNull();
        assertThat(bindTelegramAction.getActionType()).isEqualTo(IdentityActionType.BIND_TELEGRAM);
        assertThat(bindTelegramAction.getPrincipalType()).isEqualTo(IdentityPrincipalType.USER);
        assertThat(bindTelegramAction.getPrincipal()).isSameAs(principal);
        assertThat(bindTelegramAction.getIdentityUserChannel()).isNull();
        assertThat(bindTelegramAction.getPayload()).isEqualTo("{\"botUsername\":\"Identityvgbot\"}");
        assertThat(bindTelegramAction.getCreatedAt()).isEqualTo(clock.instant());
        assertThat(bindTelegramAction.getExpireAt()).isEqualTo(clock.instant().plus(Duration.ofHours(2)));
        assertThat(result.bindTelegramUrl()).isEqualTo(
                url("https://t.me/identityvgbot?startapp=" + bindTelegramAction.getId())
        );
    }

    @Test
    void confirmEmail_whenPrincipalConsentAlreadyExists_doesNotOverwriteConsent() {
        var id = UUID.randomUUID();
        var existingConsent = clock.instant().minus(Duration.ofDays(1));
        var principal = IdentityUserEntity.builder()
                .uniqueId(17L)
                .consentToKeepPersonalDataAt(existingConsent)
                .build();
        var channelEntity = IdentityUserChannelEntity.builder()
                .uniqueId(7L)
                .identityUser(principal)
                .channelType(IdentityChannelType.EMAIL)
                .channelUserId("john@example.com")
                .build();
        var verification = IdentityActionTokenEntity.builder()
                .id(id)
                .actionType(IdentityActionType.CONFIRM_EMAIL)
                .identityUserChannel(channelEntity)
                .createdAt(clock.instant().minus(Duration.ofMinutes(1)))
                .expireAt(clock.instant().plus(Duration.ofHours(2)))
                .build();
        when(actionTokenRepository.findById(id)).thenReturn(Optional.of(verification));
        when(channelRepository.save(channelEntity)).thenReturn(channelEntity);

        assertThat(service.confirmEmail(id).success()).isTrue();

        assertThat(channelEntity.getVerifiedAt()).isEqualTo(clock.instant());
        assertThat(principal.getConsentToKeepPersonalDataAt()).isEqualTo(existingConsent);
    }

    @Test
    void confirmEmail_whenTokenTypeIsNotConfirmEmail_returnsFalse() {
        var id = UUID.randomUUID();
        var channelEntity = IdentityUserChannelEntity.builder()
                .uniqueId(7L)
                .channelType(IdentityChannelType.EMAIL)
                .channelUserId("john@example.com")
                .build();
        var token = IdentityActionTokenEntity.builder()
                .id(id)
                .actionType(IdentityActionType.BIND_TELEGRAM)
                .identityUserChannel(channelEntity)
                .createdAt(clock.instant().minus(Duration.ofMinutes(1)))
                .expireAt(clock.instant().plus(Duration.ofHours(2)))
                .build();
        when(actionTokenRepository.findById(id)).thenReturn(Optional.of(token));

        assertThat(service.confirmEmail(id).success()).isFalse();

        assertThat(channelEntity.getVerifiedAt()).isNull();
        verify(channelRepository, never()).save(any(IdentityUserChannelEntity.class));
        verify(channelRepository, never()).flush();
        verify(actionTokenRepository, never()).deleteById(id);
    }

    @Test
    void confirmEmail_whenVerificationDoesNotExist_returnsFalse() {
        var id = UUID.randomUUID();
        when(actionTokenRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(service.confirmEmail(id).success()).isFalse();

        verify(channelRepository, never()).save(any(IdentityUserChannelEntity.class));
        verify(channelRepository, never()).flush();
    }

    @Test
    void confirmEmail_whenVerificationIsExpired_returnsFalse() {
        var id = UUID.randomUUID();
        var channelEntity = IdentityUserChannelEntity.builder()
                .uniqueId(7L)
                .channelType(IdentityChannelType.EMAIL)
                .channelUserId("john@example.com")
                .build();
        var verification = IdentityActionTokenEntity.builder()
                .id(id)
                .actionType(IdentityActionType.CONFIRM_EMAIL)
                .identityUserChannel(channelEntity)
                .createdAt(clock.instant().minus(Duration.ofHours(3)))
                .expireAt(clock.instant())
                .build();
        when(actionTokenRepository.findById(id)).thenReturn(Optional.of(verification));

        assertThat(service.confirmEmail(id).success()).isFalse();

        assertThat(channelEntity.getVerifiedAt()).isNull();
        verify(channelRepository, never()).save(any(IdentityUserChannelEntity.class));
        verify(channelRepository, never()).flush();
    }

    @Test
    void findConfirmEmailActionInfo_whenChannelHasUser_returnsUserUniqueId() {
        var id = UUID.randomUUID();
        var user = IdentityUserEntity.builder()
                .uniqueId(17L)
                .build();
        var channelEntity = IdentityUserChannelEntity.builder()
                .uniqueId(7L)
                .identityUser(user)
                .build();
        var verification = IdentityActionTokenEntity.builder()
                .id(id)
                .actionType(IdentityActionType.CONFIRM_EMAIL)
                .identityUserChannel(channelEntity)
                .expireAt(clock.instant().plus(Duration.ofHours(2)))
                .build();
        when(actionTokenRepository.findById(id)).thenReturn(Optional.of(verification));

        assertThat(service.findConfirmEmailActionInfo(id).userUniqueId()).isEqualTo(new UniqueId(17L));
    }

    @Test
    void confirmEmailChannel_whenActionValid_verifiesChannelConsentsUserDeletesActionAndReturnsUserUniqueId() {
        var id = UUID.randomUUID();
        var user = IdentityUserEntity.builder()
                .uniqueId(17L)
                .build();
        var channelEntity = IdentityUserChannelEntity.builder()
                .uniqueId(7L)
                .identityUser(user)
                .channelType(IdentityChannelType.EMAIL)
                .channelUserId("john@example.com")
                .build();
        var verification = IdentityActionTokenEntity.builder()
                .id(id)
                .actionType(IdentityActionType.CONFIRM_EMAIL)
                .identityUserChannel(channelEntity)
                .createdAt(clock.instant().minus(Duration.ofMinutes(1)))
                .expireAt(clock.instant().plus(Duration.ofHours(2)))
                .build();
        when(actionTokenRepository.findById(id)).thenReturn(Optional.of(verification));
        when(channelRepository.save(channelEntity)).thenReturn(channelEntity);

        assertThat(service.confirmEmailChannel(id)).isEqualTo(new UniqueId(17L));

        assertThat(channelEntity.getVerifiedAt()).isEqualTo(clock.instant());
        assertThat(user.getConsentToKeepPersonalDataAt()).isEqualTo(clock.instant());
        verify(channelRepository).save(channelEntity);
        verify(channelRepository).flush();
        verify(actionTokenRepository).deleteById(id);
        verify(actionTokenRepository, never()).save(any(IdentityActionTokenEntity.class));
    }

    @Test
    void confirmEmailChannel_whenUserAlreadyConsented_doesNotOverwriteConsent() {
        var id = UUID.randomUUID();
        var existingConsent = clock.instant().minus(Duration.ofDays(1));
        var user = IdentityUserEntity.builder()
                .uniqueId(17L)
                .consentToKeepPersonalDataAt(existingConsent)
                .build();
        var channelEntity = IdentityUserChannelEntity.builder()
                .uniqueId(7L)
                .identityUser(user)
                .channelType(IdentityChannelType.EMAIL)
                .channelUserId("john@example.com")
                .build();
        var verification = IdentityActionTokenEntity.builder()
                .id(id)
                .actionType(IdentityActionType.CONFIRM_EMAIL)
                .identityUserChannel(channelEntity)
                .createdAt(clock.instant().minus(Duration.ofMinutes(1)))
                .expireAt(clock.instant().plus(Duration.ofHours(2)))
                .build();
        when(actionTokenRepository.findById(id)).thenReturn(Optional.of(verification));
        when(channelRepository.save(channelEntity)).thenReturn(channelEntity);

        assertThat(service.confirmEmailChannel(id)).isEqualTo(new UniqueId(17L));

        assertThat(user.getConsentToKeepPersonalDataAt()).isEqualTo(existingConsent);
        verify(actionTokenRepository).deleteById(id);
    }

    @Test
    void confirmEmailChannel_whenChannelHasNoUser_returnsNullAndDoesNotDeleteAction() {
        var id = UUID.randomUUID();
        var channelEntity = IdentityUserChannelEntity.builder()
                .uniqueId(7L)
                .channelType(IdentityChannelType.EMAIL)
                .channelUserId("john@example.com")
                .build();
        var verification = IdentityActionTokenEntity.builder()
                .id(id)
                .actionType(IdentityActionType.CONFIRM_EMAIL)
                .identityUserChannel(channelEntity)
                .expireAt(clock.instant().plus(Duration.ofHours(2)))
                .build();
        when(actionTokenRepository.findById(id)).thenReturn(Optional.of(verification));

        assertThat(service.confirmEmailChannel(id)).isNull();

        verify(actionTokenRepository, never()).deleteById(id);
    }

    @Test
    void confirmEmailChannel_whenActionMissing_returnsNull() {
        var id = UUID.randomUUID();
        when(actionTokenRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(service.confirmEmailChannel(id)).isNull();

        verify(channelRepository, never()).save(any(IdentityUserChannelEntity.class));
        verify(actionTokenRepository, never()).deleteById(id);
    }

    @Test
    void confirmEmailChannel_whenActionIsNotConfirmEmail_returnsNull() {
        var id = UUID.randomUUID();
        var token = IdentityActionTokenEntity.builder()
                .id(id)
                .actionType(IdentityActionType.BIND_TELEGRAM)
                .expireAt(clock.instant().plus(Duration.ofHours(2)))
                .build();
        when(actionTokenRepository.findById(id)).thenReturn(Optional.of(token));

        assertThat(service.confirmEmailChannel(id)).isNull();

        verify(actionTokenRepository, never()).deleteById(id);
    }

    @Test
    void findBindTelegramActionInfo_whenTokenIsValid_returnsExpectedBotAndPrincipal() throws Exception {
        var id = UUID.randomUUID();
        var principal = IdentityPrincipalEntity.builder()
                .uniqueId(17L)
                .type(IdentityPrincipalType.USER)
                .build();
        var token = IdentityActionTokenEntity.builder()
                .id(id)
                .actionType(IdentityActionType.BIND_TELEGRAM)
                .principalType(IdentityPrincipalType.USER)
                .principal(principal)
                .payload("{\"botUsername\":\"Identityvgbot\"}")
                .expireAt(clock.instant().plus(Duration.ofHours(2)))
                .build();
        var expectedBot = telegramBot();
        when(actionTokenRepository.findById(id)).thenReturn(Optional.of(token));
        when(objectMapper.readValue(token.getPayload(), TelegramBotToConfirm.class))
                .thenReturn(new TelegramBotToConfirm("Identityvgbot"));
        when(applicationService.findTelegramBotByUsername("Identityvgbot")).thenReturn(expectedBot);

        var result = service.findBindTelegramActionInfo(id);

        assertThat(result).isEqualTo(new IdentityAction.BindTelegramInfo(id, expectedBot.bot(), principal));
    }

    @Test
    void findBindTelegramActionInfo_whenTokenIsNotBindTelegram_returnsNull() {
        var id = UUID.randomUUID();
        var token = IdentityActionTokenEntity.builder()
                .id(id)
                .actionType(IdentityActionType.CONFIRM_EMAIL)
                .expireAt(clock.instant().plus(Duration.ofHours(2)))
                .build();
        when(actionTokenRepository.findById(id)).thenReturn(Optional.of(token));

        assertThat(service.findBindTelegramActionInfo(id)).isNull();
    }

    private static IdentityUserChannelEmail emailChannel(Long uniqueId, String email) {
        var channel = new IdentityUserChannelEmail();
        if (uniqueId != null) {
            channel.setUniqueId(new UniqueId(uniqueId));
        }
        channel.setIdentityUserUniqueId(new UniqueId(17L));
        channel.setChannelType(IdentityChannelType.EMAIL);
        channel.setChannelUserId(email);
        return channel;
    }

    private static TelegramBotWithUrl telegramBot() {
        return new TelegramBotWithUrl(
                url("https://t.me/identityvgbot"),
                TelegramBot.builder()
                        .token("token")
                        .build()
        );
    }

    private static URL url(String value) {
        try {
            return URI.create(value).toURL();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid URL", e);
        }
    }
}
