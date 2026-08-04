package vg.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vg.identity.entity.IdentityPrincipalEntity;
import vg.identity.entity.IdentityUserEntity;
import vg.identity.model.IdentityAction;
import vg.identity.model.IdentityPrincipalType;
import vg.identity.model.IdentityUser;
import vg.identity.model.TelegramUserPrincipal;
import vg.identity.model.UserProvisioningDetails;
import vg.identity.model.application.TelegramBot;
import vg.identity.model.application.TelegramBotWithUri;
import vg.identity.repository.IdentityUserRepository;
import vg.unique.id.model.UniqueId;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramLoginServiceTest {
    private static final String BOT_NAME = "Identityvgbot";
    private static final String INIT_DATA = "init-data";
    private static final String ACTION_KEY = "7_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final Instant NOW = Instant.parse("2026-07-21T10:00:00Z");

    @Mock private IdentityActionTokenService actionTokenService;
    @Mock private IdentityActionTokenProcessorService actionTokenProcessorService;
    @Mock private TelegramAuthenticationService telegramAuthenticationService;
    @Mock private IdentityApplicationService applicationService;
    @Mock private IdentityUserChannelService channelService;
    @Mock private IdentityUserService userService;
    @Mock private IdentityUserAuthorityService authorityService;
    @Mock private IdentityUserRepository userRepository;
    @Mock private IdentityApplicationUserService applicationUserService;

    private TelegramLoginService service;

    @BeforeEach
    void setUp() {
        service = new TelegramLoginService(
                actionTokenService,
                actionTokenProcessorService,
                telegramAuthenticationService,
                applicationService,
                channelService,
                userService,
                authorityService,
                userRepository,
                applicationUserService,
                BOT_NAME,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void login_whenNoActionAndTelegramBoundToUser_authenticates() {
        var bot = bot();
        var telegramUser = TelegramUserPrincipal.builder().id(42L).firstName("John").build();
        var userEntity = IdentityUserEntity.builder().uniqueId(17L).build();
        var user = IdentityUser.builder().uniqueId(new UniqueId(17L)).build();
        when(telegramAuthenticationService.findStartParam(INIT_DATA)).thenReturn(null);
        when(applicationService.findTelegramBotByUsername(BOT_NAME)).thenReturn(bot);
        when(telegramAuthenticationService.parseUser(bot.bot(), INIT_DATA)).thenReturn(Optional.of(telegramUser));
        when(channelService.findUserByTelegramId(42L)).thenReturn(userEntity);
        when(userService.toModel(userEntity)).thenReturn(user);

        assertThat(service.login(INIT_DATA, false).outcome()).isEqualTo(TelegramLoginService.Result.Outcome.AUTHENTICATED);
        verify(applicationUserService).recordAuthentication(bot.bot().applicationId(), 17L);
    }

    @Test
    void login_whenConfirmEmailActionAndConsentMissing_returnsConsentRequired() {
        when(telegramAuthenticationService.findStartParam(INIT_DATA)).thenReturn(ACTION_KEY);
        when(actionTokenService.findConfirmEmailActionInfo(ACTION_KEY)).thenReturn(
                IdentityAction.ConfirmEmailInfo.builder().actionKey(ACTION_KEY).personalInformationConsentGiven(false).build()
        );

        assertThat(service.login(INIT_DATA, false).outcome()).isEqualTo(TelegramLoginService.Result.Outcome.CONSENT_REQUIRED);
    }

    @Test
    void login_whenBindTelegramActionAndUserAlreadyConsented_proceedsWithoutConsentFlag() {
        var bot = TelegramBot.builder().token("token").build();
        var principal = IdentityPrincipalEntity.builder().uniqueId(17L).type(IdentityPrincipalType.USER).build();
        var userEntity = userEntity(17L, NOW.minusSeconds(3600));
        var telegramUser = TelegramUserPrincipal.builder().id(42L).build();
        var user = IdentityUser.builder().uniqueId(new UniqueId(17L)).build();
        when(telegramAuthenticationService.findStartParam(INIT_DATA)).thenReturn(ACTION_KEY);
        when(actionTokenService.findBindTelegramActionInfo(ACTION_KEY)).thenReturn(new IdentityAction.BindTelegramInfo(7L, bot, principal));
        when(userRepository.findById(17L)).thenReturn(Optional.of(userEntity));
        when(telegramAuthenticationService.parseUser(bot, INIT_DATA)).thenReturn(Optional.of(telegramUser));
        when(channelService.bindTelegramUser(telegramUser, userEntity)).thenReturn(IdentityUserChannelService.TelegramBindResult.SUCCESS);
        when(userService.toModel(userEntity)).thenReturn(user);

        assertThat(service.login(INIT_DATA, true).outcome()).isEqualTo(TelegramLoginService.Result.Outcome.AUTHENTICATED);
        verify(actionTokenProcessorService).consumeAction(7L);
    }

    @Test
    void login_whenNoActionAndTelegramNotBound_returnsGreeting() {
        var telegramUser = telegramUser();
        when(telegramAuthenticationService.findStartParam(INIT_DATA)).thenReturn(null);
        when(applicationService.findTelegramBotByUsername(BOT_NAME)).thenReturn(bot());
        when(telegramAuthenticationService.parseUser(bot().bot(), INIT_DATA)).thenReturn(Optional.of(telegramUser));
        when(channelService.findUserByTelegramId(42L)).thenReturn(null);

        var result = service.login(INIT_DATA, false);

        assertThat(result.outcome()).isEqualTo(TelegramLoginService.Result.Outcome.GREETING);
        assertThat(result.greetingName()).isEqualTo("John");
    }

    @Test
    void login_whenNoActionAndBotApplicationNotFound_returnsFailed() {
        when(telegramAuthenticationService.findStartParam(INIT_DATA)).thenReturn(null);
        when(applicationService.findTelegramBotByUsername(BOT_NAME)).thenReturn(null);

        assertThat(service.login(INIT_DATA, false).outcome()).isEqualTo(TelegramLoginService.Result.Outcome.FAILED);
    }

    @Test
    void login_whenBotNameNotConfigured_returnsFailed() {
        var serviceWithoutBot = new TelegramLoginService(
                actionTokenService, actionTokenProcessorService, telegramAuthenticationService, applicationService,
                channelService, userService, authorityService, userRepository, applicationUserService, "",
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        when(telegramAuthenticationService.findStartParam(INIT_DATA)).thenReturn(null);

        assertThat(serviceWithoutBot.login(INIT_DATA, false).outcome()).isEqualTo(TelegramLoginService.Result.Outcome.FAILED);
        verify(applicationService, never()).findTelegramBotByUsername(any());
    }

    @Test
    void login_whenNoActionAndInitDataInvalid_returnsFailed() {
        when(telegramAuthenticationService.findStartParam(INIT_DATA)).thenReturn(null);
        when(applicationService.findTelegramBotByUsername(BOT_NAME)).thenReturn(bot());
        when(telegramAuthenticationService.parseUser(bot().bot(), INIT_DATA)).thenReturn(Optional.empty());

        assertThat(service.login(INIT_DATA, false).outcome()).isEqualTo(TelegramLoginService.Result.Outcome.FAILED);
    }

    @Test
    void login_whenStartParamIsMalformedActionKey_returnsFailed() {
        when(telegramAuthenticationService.findStartParam(INIT_DATA)).thenReturn("not-an-action-key");
        when(actionTokenService.findConfirmEmailActionInfo("not-an-action-key")).thenReturn(null);
        when(actionTokenService.findBindTelegramActionInfo("not-an-action-key")).thenReturn(null);

        assertThat(service.login(INIT_DATA, false).outcome()).isEqualTo(TelegramLoginService.Result.Outcome.FAILED);
    }

    @Test
    void login_whenBindTelegramActionAndConsentMissing_returnsConsentRequired() {
        stubAction();
        when(actionTokenService.findBindTelegramActionInfo(ACTION_KEY)).thenReturn(bindInfo());
        when(userRepository.findById(17L)).thenReturn(Optional.of(userEntity(17L, null)));

        assertThat(service.login(INIT_DATA, false).outcome()).isEqualTo(TelegramLoginService.Result.Outcome.CONSENT_REQUIRED);
        verify(channelService, never()).bindTelegramUser(any(), any());
        verify(actionTokenProcessorService, never()).consumeAction(any());
    }

    @Test
    void login_whenBindTelegramActionAndConsentGranted_bindsSetsConsentConsumesAndAuthenticates() {
        var entity = userEntity(17L, null);
        var telegramUser = telegramUser();
        stubAction();
        when(actionTokenService.findBindTelegramActionInfo(ACTION_KEY)).thenReturn(bindInfo());
        when(userRepository.findById(17L)).thenReturn(Optional.of(entity));
        when(telegramAuthenticationService.parseUser(bindInfo().telegramBot(), INIT_DATA)).thenReturn(Optional.of(telegramUser));
        when(channelService.bindTelegramUser(telegramUser, entity)).thenReturn(IdentityUserChannelService.TelegramBindResult.SUCCESS);
        when(userService.toModel(entity)).thenReturn(identityUser());

        assertThat(service.login(INIT_DATA, true).outcome()).isEqualTo(TelegramLoginService.Result.Outcome.AUTHENTICATED);
        assertThat(entity.getConsentToKeepPersonalDataAt()).isEqualTo(NOW);
        verify(userRepository).save(entity);
        verify(actionTokenProcessorService).consumeAction(7L);
    }

    @Test
    void login_whenBindTelegramActionAndUserMissing_returnsFailed() {
        stubAction();
        when(actionTokenService.findBindTelegramActionInfo(ACTION_KEY)).thenReturn(bindInfo());
        when(userRepository.findById(17L)).thenReturn(Optional.empty());

        assertThat(service.login(INIT_DATA, true).outcome()).isEqualTo(TelegramLoginService.Result.Outcome.FAILED);
        verify(channelService, never()).bindTelegramUser(any(), any());
    }

    @Test
    void login_whenBindTelegramActionAndInitDataInvalid_returnsFailed() {
        stubAction();
        when(actionTokenService.findBindTelegramActionInfo(ACTION_KEY)).thenReturn(bindInfo());
        when(userRepository.findById(17L)).thenReturn(Optional.of(userEntity(17L, NOW)));
        when(telegramAuthenticationService.parseUser(bindInfo().telegramBot(), INIT_DATA)).thenReturn(Optional.empty());

        assertThat(service.login(INIT_DATA, true).outcome()).isEqualTo(TelegramLoginService.Result.Outcome.FAILED);
        verify(actionTokenProcessorService, never()).consumeAction(any());
    }

    @Test
    void login_whenBindTelegramActionAndChannelBelongsToAnotherUser_returnsFailedWithoutConsuming() {
        var entity = userEntity(17L, NOW);
        var telegramUser = telegramUser();
        stubAction();
        when(actionTokenService.findBindTelegramActionInfo(ACTION_KEY)).thenReturn(bindInfo());
        when(userRepository.findById(17L)).thenReturn(Optional.of(entity));
        when(telegramAuthenticationService.parseUser(bindInfo().telegramBot(), INIT_DATA)).thenReturn(Optional.of(telegramUser));
        when(channelService.bindTelegramUser(telegramUser, entity))
                .thenReturn(IdentityUserChannelService.TelegramBindResult.CHANNEL_ATTACHED_TO_ANOTHER_USER);

        assertThat(service.login(INIT_DATA, true).outcome()).isEqualTo(TelegramLoginService.Result.Outcome.FAILED);
        verify(actionTokenProcessorService, never()).consumeAction(any());
    }

    @Test
    void login_whenConfirmEmailActionAndConsentGranted_confirmsAndAuthenticates() {
        var entity = userEntity(17L, null);
        var telegramUser = telegramUser();
        stubAction();
        when(actionTokenService.findConfirmEmailActionInfo(ACTION_KEY)).thenReturn(confirmInfo(false));
        when(applicationService.findTelegramBotByUsername(BOT_NAME)).thenReturn(bot());
        when(telegramAuthenticationService.parseUser(bot().bot(), INIT_DATA)).thenReturn(Optional.of(telegramUser));
        when(actionTokenProcessorService.confirmEmailWithTelegram(ACTION_KEY, telegramUser, null)).thenReturn(entity);
        when(userService.toModel(entity)).thenReturn(identityUser());

        assertThat(service.login(INIT_DATA, true).outcome()).isEqualTo(TelegramLoginService.Result.Outcome.AUTHENTICATED);
        verify(actionTokenProcessorService).confirmEmailWithTelegram(ACTION_KEY, telegramUser, null);
    }

    @Test
    void login_whenConfirmEmailActionAlreadyConsented_proceedsWithoutConsentFlag() {
        var entity = userEntity(17L, NOW);
        var telegramUser = telegramUser();
        stubAction();
        when(actionTokenService.findConfirmEmailActionInfo(ACTION_KEY)).thenReturn(confirmInfo(true));
        when(applicationService.findTelegramBotByUsername(BOT_NAME)).thenReturn(bot());
        when(telegramAuthenticationService.parseUser(bot().bot(), INIT_DATA)).thenReturn(Optional.of(telegramUser));
        when(actionTokenProcessorService.confirmEmailWithTelegram(ACTION_KEY, telegramUser, null)).thenReturn(entity);
        when(userService.toModel(entity)).thenReturn(identityUser());

        assertThat(service.login(INIT_DATA, false).outcome()).isEqualTo(TelegramLoginService.Result.Outcome.AUTHENTICATED);
    }

    @Test
    void login_whenConfirmEmailActionAndInitDataInvalid_returnsFailed() {
        stubAction();
        when(actionTokenService.findConfirmEmailActionInfo(ACTION_KEY)).thenReturn(confirmInfo(true));
        when(applicationService.findTelegramBotByUsername(BOT_NAME)).thenReturn(bot());
        when(telegramAuthenticationService.parseUser(bot().bot(), INIT_DATA)).thenReturn(Optional.empty());

        assertThat(service.login(INIT_DATA, false).outcome()).isEqualTo(TelegramLoginService.Result.Outcome.FAILED);
        verify(actionTokenProcessorService, never()).confirmEmailWithTelegram(any(), any(), any());
    }

    @Test
    void login_whenConfirmEmailNeedsCredentials_returnsCredentialsRequiredWithSuggestedDisplayName() {
        var telegramUser = telegramUser();
        stubAction();
        when(actionTokenService.findConfirmEmailActionInfo(ACTION_KEY)).thenReturn(confirmInfo(true));
        when(applicationService.findTelegramBotByUsername(BOT_NAME)).thenReturn(bot());
        when(telegramAuthenticationService.parseUser(bot().bot(), INIT_DATA)).thenReturn(Optional.of(telegramUser));
        doThrow(new IdentityActionTokenProcessorService.CredentialsRequiredException())
                .when(actionTokenProcessorService).confirmEmailWithTelegram(ACTION_KEY, telegramUser, null);

        var result = service.login(INIT_DATA, true);

        assertThat(result.outcome()).isEqualTo(TelegramLoginService.Result.Outcome.CREDENTIALS_REQUIRED);
        assertThat(result.suggestedDisplayName()).isEqualTo("John");
    }

    @Test
    void login_whenConfirmEmailWithCredentials_provisionsAndAuthenticates() {
        var entity = userEntity(17L, null);
        var telegramUser = telegramUser();
        var provisioning = new UserProvisioningDetails("John", "Abcdefghi1", true);
        stubAction();
        when(actionTokenService.findConfirmEmailActionInfo(ACTION_KEY)).thenReturn(confirmInfo(true));
        when(applicationService.findTelegramBotByUsername(BOT_NAME)).thenReturn(bot());
        when(telegramAuthenticationService.parseUser(bot().bot(), INIT_DATA)).thenReturn(Optional.of(telegramUser));
        when(actionTokenProcessorService.confirmEmailWithTelegram(ACTION_KEY, telegramUser, provisioning)).thenReturn(entity);
        when(userService.toModel(entity)).thenReturn(identityUser());

        assertThat(service.login(INIT_DATA, true, provisioning).outcome()).isEqualTo(TelegramLoginService.Result.Outcome.AUTHENTICATED);
        verify(actionTokenProcessorService).confirmEmailWithTelegram(ACTION_KEY, telegramUser, provisioning);
    }

    @Test
    void login_whenConfirmEmailActionAndBindConflict_returnsFailedWithoutConsumingTheAction() {
        var telegramUser = telegramUser();
        stubAction();
        when(actionTokenService.findConfirmEmailActionInfo(ACTION_KEY)).thenReturn(confirmInfo(true));
        when(applicationService.findTelegramBotByUsername(BOT_NAME)).thenReturn(bot());
        when(telegramAuthenticationService.parseUser(bot().bot(), INIT_DATA)).thenReturn(Optional.of(telegramUser));
        doThrow(new IdentityActionTokenProcessorService.TelegramChannelConflictException())
                .when(actionTokenProcessorService).confirmEmailWithTelegram(ACTION_KEY, telegramUser, null);

        assertThat(service.login(INIT_DATA, false).outcome()).isEqualTo(TelegramLoginService.Result.Outcome.FAILED);
        verify(actionTokenProcessorService).confirmEmailWithTelegram(ACTION_KEY, telegramUser, null);
    }

    @Test
    void login_whenConfirmEmailCannotResolveAUser_returnsFailed() {
        var telegramUser = telegramUser();
        stubAction();
        when(actionTokenService.findConfirmEmailActionInfo(ACTION_KEY)).thenReturn(confirmInfo(true));
        when(applicationService.findTelegramBotByUsername(BOT_NAME)).thenReturn(bot());
        when(telegramAuthenticationService.parseUser(bot().bot(), INIT_DATA)).thenReturn(Optional.of(telegramUser));
        when(actionTokenProcessorService.confirmEmailWithTelegram(ACTION_KEY, telegramUser, null)).thenReturn(null);

        assertThat(service.login(INIT_DATA, false).outcome()).isEqualTo(TelegramLoginService.Result.Outcome.FAILED);
    }

    @Test
    void login_whenActionKeyMatchesNoAction_returnsFailed() {
        stubAction();
        when(actionTokenService.findConfirmEmailActionInfo(ACTION_KEY)).thenReturn(null);
        when(actionTokenService.findBindTelegramActionInfo(ACTION_KEY)).thenReturn(null);

        assertThat(service.login(INIT_DATA, false).outcome()).isEqualTo(TelegramLoginService.Result.Outcome.FAILED);
    }

    private static TelegramBotWithUri bot() {
        return new TelegramBotWithUri(URI.create("https://t.me/identityvgbot"), TelegramBot.builder().token("token").applicationId(new UniqueId(99L)).build());
    }

    private void stubAction() {
        when(telegramAuthenticationService.findStartParam(INIT_DATA)).thenReturn(ACTION_KEY);
    }

    private static IdentityAction.ConfirmEmailInfo confirmInfo(boolean consentGiven) {
        return IdentityAction.ConfirmEmailInfo.builder()
                .actionKey(ACTION_KEY)
                .personalInformationConsentGiven(consentGiven)
                .build();
    }

    private static IdentityAction.BindTelegramInfo bindInfo() {
        return new IdentityAction.BindTelegramInfo(
                7L,
                TelegramBot.builder().token("action-token").build(),
                IdentityPrincipalEntity.builder().uniqueId(17L).type(IdentityPrincipalType.USER).build()
        );
    }

    private static IdentityUserEntity userEntity(long id, Instant consentAt) {
        return IdentityUserEntity.builder().uniqueId(id).consentToKeepPersonalDataAt(consentAt).build();
    }

    private static IdentityUser identityUser() {
        return IdentityUser.builder().uniqueId(new UniqueId(17L)).build();
    }

    private static TelegramUserPrincipal telegramUser() {
        return TelegramUserPrincipal.builder().id(42L).firstName("John").build();
    }
}
