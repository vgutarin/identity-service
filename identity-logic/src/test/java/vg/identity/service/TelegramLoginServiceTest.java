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
import java.util.UUID;

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
    private static final Instant NOW = Instant.parse("2026-07-21T10:00:00Z");
    private static final UniqueId APPLICATION_ID = new UniqueId(99L);

    @Mock
    private IdentityActionTokenService actionTokenService;
    @Mock
    private IdentityActionTokenProcessorService actionTokenProcessorService;
    @Mock
    private TelegramAuthenticationService telegramAuthenticationService;
    @Mock
    private IdentityApplicationService applicationService;
    @Mock
    private IdentityUserChannelService channelService;
    @Mock
    private IdentityUserService userService;
    @Mock
    private IdentityUserAuthorityService authorityService;
    @Mock
    private IdentityUserRepository userRepository;
    @Mock
    private IdentityApplicationUserService applicationUserService;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

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
                clock
        );
    }

    // --- No action id: plain login / greeting ---------------------------------------------------------------

    @Test
    void login_whenNoActionAndTelegramBoundToUser_authenticates() {
        var botWithUrl = botWithUrl();
        var telegramUser = telegramUser(42L, "John");
        var userEntity = userEntity(17L, null);
        var identityUser = identityUser(17L);
        when(telegramAuthenticationService.findStartParam(INIT_DATA)).thenReturn(null);
        when(applicationService.findTelegramBotByUsername(BOT_NAME)).thenReturn(botWithUrl);
        when(telegramAuthenticationService.parseUser(botWithUrl.bot(), INIT_DATA)).thenReturn(Optional.of(telegramUser));
        when(channelService.findUserByTelegramId(42L)).thenReturn(userEntity);
        when(userService.toModel(userEntity)).thenReturn(identityUser);

        var result = service.login(INIT_DATA, false);

        assertThat(result.outcome()).isEqualTo(TelegramLoginService.Result.Outcome.AUTHENTICATED);
        assertThat(result.user()).isSameAs(identityUser);
        verify(authorityService).loadAuthorities(identityUser);
        verify(applicationUserService).recordAuthentication(APPLICATION_ID, 17L);
    }

    @Test
    void login_whenNoActionAndTelegramNotBound_returnsGreeting() {
        var botWithUrl = botWithUrl();
        var telegramUser = telegramUser(42L, "John");
        when(telegramAuthenticationService.findStartParam(INIT_DATA)).thenReturn(null);
        when(applicationService.findTelegramBotByUsername(BOT_NAME)).thenReturn(botWithUrl);
        when(telegramAuthenticationService.parseUser(botWithUrl.bot(), INIT_DATA)).thenReturn(Optional.of(telegramUser));
        when(channelService.findUserByTelegramId(42L)).thenReturn(null);

        var result = service.login(INIT_DATA, false);

        assertThat(result.outcome()).isEqualTo(TelegramLoginService.Result.Outcome.GREETING);
        assertThat(result.greetingName()).isEqualTo("John");
    }

    @Test
    void login_whenNoActionAndBotApplicationNotFound_returnsFailed() {
        when(telegramAuthenticationService.findStartParam(INIT_DATA)).thenReturn(null);
        when(applicationService.findTelegramBotByUsername(BOT_NAME)).thenReturn(null);

        var result = service.login(INIT_DATA, false);

        assertThat(result.outcome()).isEqualTo(TelegramLoginService.Result.Outcome.FAILED);
    }

    @Test
    void login_whenBotNameNotConfigured_returnsFailed() {
        var serviceWithoutBot = new TelegramLoginService(
                actionTokenService,
                actionTokenProcessorService,
                telegramAuthenticationService,
                applicationService,
                channelService,
                userService,
                authorityService,
                userRepository,
                applicationUserService,
                "",
                clock
        );
        when(telegramAuthenticationService.findStartParam(INIT_DATA)).thenReturn(null);

        var result = serviceWithoutBot.login(INIT_DATA, false);

        assertThat(result.outcome()).isEqualTo(TelegramLoginService.Result.Outcome.FAILED);
        verify(applicationService, never()).findTelegramBotByUsername(any());
    }

    @Test
    void login_whenNoActionAndInitDataInvalid_returnsFailed() {
        var botWithUrl = botWithUrl();
        when(telegramAuthenticationService.findStartParam(INIT_DATA)).thenReturn(null);
        when(applicationService.findTelegramBotByUsername(BOT_NAME)).thenReturn(botWithUrl);
        when(telegramAuthenticationService.parseUser(botWithUrl.bot(), INIT_DATA)).thenReturn(Optional.empty());

        var result = service.login(INIT_DATA, false);

        assertThat(result.outcome()).isEqualTo(TelegramLoginService.Result.Outcome.FAILED);
    }

    @Test
    void login_whenStartParamIsNotAUuid_treatsAsNoAction() {
        var botWithUrl = botWithUrl();
        var telegramUser = telegramUser(42L, "John");
        when(telegramAuthenticationService.findStartParam(INIT_DATA)).thenReturn("not-a-uuid");
        when(applicationService.findTelegramBotByUsername(BOT_NAME)).thenReturn(botWithUrl);
        when(telegramAuthenticationService.parseUser(botWithUrl.bot(), INIT_DATA)).thenReturn(Optional.of(telegramUser));
        when(channelService.findUserByTelegramId(42L)).thenReturn(null);

        var result = service.login(INIT_DATA, false);

        assertThat(result.outcome()).isEqualTo(TelegramLoginService.Result.Outcome.GREETING);
        verify(actionTokenService, never()).findConfirmEmailActionInfo(any());
        verify(actionTokenService, never()).findBindTelegramActionInfo(any());
    }

    // --- Action id: BIND_TELEGRAM ---------------------------------------------------------------------------

    @Test
    void login_whenBindTelegramActionAndConsentMissing_returnsConsentRequired() {
        var actionId = UUID.randomUUID();
        var actionBot = TelegramBot.builder().token("action-token").build();
        var principal = principal(17L);
        stubAction(actionId);
        when(actionTokenService.findBindTelegramActionInfo(actionId))
                .thenReturn(new IdentityAction.BindTelegramInfo(actionId, actionBot, principal));
        when(userRepository.findById(17L)).thenReturn(Optional.of(userEntity(17L, null)));

        var result = service.login(INIT_DATA, false);

        assertThat(result.outcome()).isEqualTo(TelegramLoginService.Result.Outcome.CONSENT_REQUIRED);
        verify(channelService, never()).bindTelegramUser(any(), any());
        verify(actionTokenProcessorService, never()).consumeAction(any());
    }

    @Test
    void login_whenBindTelegramActionAndConsentGranted_bindsSetsConsentConsumesAndAuthenticates() {
        var actionId = UUID.randomUUID();
        var actionBot = TelegramBot.builder().token("action-token").build();
        var principal = principal(17L);
        var userEntity = userEntity(17L, null);
        var telegramUser = telegramUser(42L, "John");
        var identityUser = identityUser(17L);
        stubAction(actionId);
        when(actionTokenService.findBindTelegramActionInfo(actionId))
                .thenReturn(new IdentityAction.BindTelegramInfo(actionId, actionBot, principal));
        when(userRepository.findById(17L)).thenReturn(Optional.of(userEntity));
        when(telegramAuthenticationService.parseUser(actionBot, INIT_DATA)).thenReturn(Optional.of(telegramUser));
        when(channelService.bindTelegramUser(telegramUser, userEntity))
                .thenReturn(IdentityUserChannelService.TelegramBindResult.SUCCESS);
        when(userService.toModel(userEntity)).thenReturn(identityUser);

        var result = service.login(INIT_DATA, true);

        assertThat(result.outcome()).isEqualTo(TelegramLoginService.Result.Outcome.AUTHENTICATED);
        assertThat(result.user()).isSameAs(identityUser);
        assertThat(userEntity.getConsentToKeepPersonalDataAt()).isEqualTo(NOW);
        verify(userRepository).save(userEntity);
        verify(actionTokenProcessorService).consumeAction(actionId);
        verify(authorityService).loadAuthorities(identityUser);
    }

    @Test
    void login_whenBindTelegramActionAndUserAlreadyConsented_proceedsWithoutConsentFlag() {
        var actionId = UUID.randomUUID();
        var actionBot = TelegramBot.builder().token("action-token").build();
        var principal = principal(17L);
        var userEntity = userEntity(17L, NOW.minusSeconds(3600));
        var telegramUser = telegramUser(42L, "John");
        var identityUser = identityUser(17L);
        stubAction(actionId);
        when(actionTokenService.findBindTelegramActionInfo(actionId))
                .thenReturn(new IdentityAction.BindTelegramInfo(actionId, actionBot, principal));
        when(userRepository.findById(17L)).thenReturn(Optional.of(userEntity));
        when(telegramAuthenticationService.parseUser(actionBot, INIT_DATA)).thenReturn(Optional.of(telegramUser));
        when(channelService.bindTelegramUser(telegramUser, userEntity))
                .thenReturn(IdentityUserChannelService.TelegramBindResult.SUCCESS);
        when(userService.toModel(userEntity)).thenReturn(identityUser);

        var result = service.login(INIT_DATA, false);

        assertThat(result.outcome()).isEqualTo(TelegramLoginService.Result.Outcome.AUTHENTICATED);
        verify(userRepository, never()).save(any());
        verify(actionTokenProcessorService).consumeAction(actionId);
    }

    @Test
    void login_whenBindTelegramActionAndUserMissing_returnsFailed() {
        var actionId = UUID.randomUUID();
        var actionBot = TelegramBot.builder().token("action-token").build();
        stubAction(actionId);
        when(actionTokenService.findBindTelegramActionInfo(actionId))
                .thenReturn(new IdentityAction.BindTelegramInfo(actionId, actionBot, principal(17L)));
        when(userRepository.findById(17L)).thenReturn(Optional.empty());

        var result = service.login(INIT_DATA, true);

        assertThat(result.outcome()).isEqualTo(TelegramLoginService.Result.Outcome.FAILED);
        verify(channelService, never()).bindTelegramUser(any(), any());
    }

    @Test
    void login_whenBindTelegramActionAndInitDataInvalid_returnsFailed() {
        var actionId = UUID.randomUUID();
        var actionBot = TelegramBot.builder().token("action-token").build();
        stubAction(actionId);
        when(actionTokenService.findBindTelegramActionInfo(actionId))
                .thenReturn(new IdentityAction.BindTelegramInfo(actionId, actionBot, principal(17L)));
        when(userRepository.findById(17L)).thenReturn(Optional.of(userEntity(17L, NOW)));
        when(telegramAuthenticationService.parseUser(actionBot, INIT_DATA)).thenReturn(Optional.empty());

        var result = service.login(INIT_DATA, true);

        assertThat(result.outcome()).isEqualTo(TelegramLoginService.Result.Outcome.FAILED);
        verify(channelService, never()).bindTelegramUser(any(), any());
        verify(actionTokenProcessorService, never()).consumeAction(any());
    }

    @Test
    void login_whenBindTelegramActionAndChannelBelongsToAnotherUser_returnsFailedWithoutConsuming() {
        var actionId = UUID.randomUUID();
        var actionBot = TelegramBot.builder().token("action-token").build();
        var userEntity = userEntity(17L, NOW);
        var telegramUser = telegramUser(42L, "John");
        stubAction(actionId);
        when(actionTokenService.findBindTelegramActionInfo(actionId))
                .thenReturn(new IdentityAction.BindTelegramInfo(actionId, actionBot, principal(17L)));
        when(userRepository.findById(17L)).thenReturn(Optional.of(userEntity));
        when(telegramAuthenticationService.parseUser(actionBot, INIT_DATA)).thenReturn(Optional.of(telegramUser));
        when(channelService.bindTelegramUser(telegramUser, userEntity))
                .thenReturn(IdentityUserChannelService.TelegramBindResult.CHANNEL_ATTACHED_TO_ANOTHER_USER);

        var result = service.login(INIT_DATA, true);

        assertThat(result.outcome()).isEqualTo(TelegramLoginService.Result.Outcome.FAILED);
        verify(actionTokenProcessorService, never()).consumeAction(any());
    }

    // --- Action id: CONFIRM_EMAIL -------------------------------------------------------------------------

    @Test
    void login_whenConfirmEmailActionAndConsentMissing_returnsConsentRequired() {
        var actionId = UUID.randomUUID();
        stubAction(actionId);
        when(actionTokenService.findConfirmEmailActionInfo(actionId)).thenReturn(confirmEmailInfo(actionId, new UniqueId(17L), false));

        var result = service.login(INIT_DATA, false);

        assertThat(result.outcome()).isEqualTo(TelegramLoginService.Result.Outcome.CONSENT_REQUIRED);
        verify(actionTokenProcessorService, never()).confirmEmailWithTelegram(any(), any(), any());
    }

    @Test
    void login_whenConfirmEmailActionAndConsentGranted_confirmsAndAuthenticates() {
        var actionId = UUID.randomUUID();
        var botWithUrl = botWithUrl();
        var userEntity = userEntity(17L, null);
        var telegramUser = telegramUser(42L, "John");
        var identityUser = identityUser(17L);
        stubAction(actionId);
        when(actionTokenService.findConfirmEmailActionInfo(actionId)).thenReturn(confirmEmailInfo(actionId, new UniqueId(17L), false));
        when(applicationService.findTelegramBotByUsername(BOT_NAME)).thenReturn(botWithUrl);
        when(telegramAuthenticationService.parseUser(botWithUrl.bot(), INIT_DATA)).thenReturn(Optional.of(telegramUser));
        when(actionTokenProcessorService.confirmEmailWithTelegram(actionId, telegramUser, null)).thenReturn(userEntity);
        when(userService.toModel(userEntity)).thenReturn(identityUser);

        var result = service.login(INIT_DATA, true);

        assertThat(result.outcome()).isEqualTo(TelegramLoginService.Result.Outcome.AUTHENTICATED);
        assertThat(result.user()).isSameAs(identityUser);
        verify(actionTokenProcessorService).confirmEmailWithTelegram(actionId, telegramUser, null);
        verify(authorityService).loadAuthorities(identityUser);
        verify(applicationUserService).recordAuthentication(APPLICATION_ID, 17L);
    }

    @Test
    void login_whenConfirmEmailActionAlreadyConsented_proceedsWithoutConsentFlag() {
        var actionId = UUID.randomUUID();
        var botWithUrl = botWithUrl();
        var userEntity = userEntity(17L, NOW);
        var telegramUser = telegramUser(42L, "John");
        var identityUser = identityUser(17L);
        stubAction(actionId);
        when(actionTokenService.findConfirmEmailActionInfo(actionId)).thenReturn(confirmEmailInfo(actionId, new UniqueId(17L), true));
        when(applicationService.findTelegramBotByUsername(BOT_NAME)).thenReturn(botWithUrl);
        when(telegramAuthenticationService.parseUser(botWithUrl.bot(), INIT_DATA)).thenReturn(Optional.of(telegramUser));
        when(actionTokenProcessorService.confirmEmailWithTelegram(actionId, telegramUser, null)).thenReturn(userEntity);
        when(userService.toModel(userEntity)).thenReturn(identityUser);

        var result = service.login(INIT_DATA, false);

        assertThat(result.outcome()).isEqualTo(TelegramLoginService.Result.Outcome.AUTHENTICATED);
    }

    @Test
    void login_whenConfirmEmailActionAndInitDataInvalid_returnsFailed() {
        var actionId = UUID.randomUUID();
        var botWithUrl = botWithUrl();
        stubAction(actionId);
        when(actionTokenService.findConfirmEmailActionInfo(actionId)).thenReturn(confirmEmailInfo(actionId, new UniqueId(17L), true));
        when(applicationService.findTelegramBotByUsername(BOT_NAME)).thenReturn(botWithUrl);
        when(telegramAuthenticationService.parseUser(botWithUrl.bot(), INIT_DATA)).thenReturn(Optional.empty());

        var result = service.login(INIT_DATA, false);

        assertThat(result.outcome()).isEqualTo(TelegramLoginService.Result.Outcome.FAILED);
        verify(actionTokenProcessorService, never()).confirmEmailWithTelegram(any(), any(), any());
    }

    @Test
    void login_whenConfirmEmailNeedsCredentials_returnsCredentialsRequiredWithSuggestedDisplayName() {
        var actionId = UUID.randomUUID();
        var botWithUrl = botWithUrl();
        var telegramUser = telegramUser(42L, "John");
        stubAction(actionId);
        when(actionTokenService.findConfirmEmailActionInfo(actionId)).thenReturn(confirmEmailInfo(actionId, null, true));
        when(applicationService.findTelegramBotByUsername(BOT_NAME)).thenReturn(botWithUrl);
        when(telegramAuthenticationService.parseUser(botWithUrl.bot(), INIT_DATA))
                .thenReturn(Optional.of(telegramUser));
        doThrow(new IdentityActionTokenProcessorService.CredentialsRequiredException())
                .when(actionTokenProcessorService)
                .confirmEmailWithTelegram(actionId, telegramUser, null);

        var result = service.login(INIT_DATA, true);

        assertThat(result.outcome()).isEqualTo(TelegramLoginService.Result.Outcome.CREDENTIALS_REQUIRED);
        assertThat(result.suggestedDisplayName()).isEqualTo(telegramUser.displayName());
    }

    @Test
    void login_whenConfirmEmailWithCredentials_provisionsAndAuthenticates() {
        var actionId = UUID.randomUUID();
        var botWithUrl = botWithUrl();
        var telegramUser = telegramUser(42L, "John");
        var userEntity = userEntity(17L, null);
        var identityUser = identityUser(17L);
        var provisioning = new UserProvisioningDetails("John", "Abcdefghi1", true);
        stubAction(actionId);
        when(actionTokenService.findConfirmEmailActionInfo(actionId)).thenReturn(confirmEmailInfo(actionId, null, true));
        when(applicationService.findTelegramBotByUsername(BOT_NAME)).thenReturn(botWithUrl);
        when(telegramAuthenticationService.parseUser(botWithUrl.bot(), INIT_DATA))
                .thenReturn(Optional.of(telegramUser));
        when(actionTokenProcessorService.confirmEmailWithTelegram(actionId, telegramUser, provisioning))
                .thenReturn(userEntity);
        when(userService.toModel(userEntity)).thenReturn(identityUser);

        var result = service.login(INIT_DATA, true, provisioning);

        assertThat(result.outcome()).isEqualTo(TelegramLoginService.Result.Outcome.AUTHENTICATED);
        assertThat(result.user()).isSameAs(identityUser);
        verify(actionTokenProcessorService).confirmEmailWithTelegram(actionId, telegramUser, provisioning);
    }

    @Test
    void login_whenConfirmEmailActionAndBindConflict_returnsFailedWithoutConsumingTheAction() {
        var actionId = UUID.randomUUID();
        var botWithUrl = botWithUrl();
        var telegramUser = telegramUser(42L, "John");
        stubAction(actionId);
        when(actionTokenService.findConfirmEmailActionInfo(actionId)).thenReturn(confirmEmailInfo(actionId, new UniqueId(17L), true));
        when(applicationService.findTelegramBotByUsername(BOT_NAME)).thenReturn(botWithUrl);
        when(telegramAuthenticationService.parseUser(botWithUrl.bot(), INIT_DATA)).thenReturn(Optional.of(telegramUser));
        doThrow(new IdentityActionTokenProcessorService.TelegramChannelConflictException())
                .when(actionTokenProcessorService)
                .confirmEmailWithTelegram(actionId, telegramUser, null);

        var result = service.login(INIT_DATA, false);

        assertThat(result.outcome()).isEqualTo(TelegramLoginService.Result.Outcome.FAILED);
        verify(actionTokenProcessorService).confirmEmailWithTelegram(actionId, telegramUser, null);
    }

    @Test
    void login_whenConfirmEmailCannotResolveAUser_returnsFailed() {
        var actionId = UUID.randomUUID();
        var botWithUrl = botWithUrl();
        var telegramUser = telegramUser(42L, "John");
        stubAction(actionId);
        when(actionTokenService.findConfirmEmailActionInfo(actionId)).thenReturn(confirmEmailInfo(actionId, new UniqueId(17L), true));
        when(applicationService.findTelegramBotByUsername(BOT_NAME)).thenReturn(botWithUrl);
        when(telegramAuthenticationService.parseUser(botWithUrl.bot(), INIT_DATA)).thenReturn(Optional.of(telegramUser));
        when(actionTokenProcessorService.confirmEmailWithTelegram(actionId, telegramUser, null)).thenReturn(null);

        assertThat(service.login(INIT_DATA, false).outcome())
                .isEqualTo(TelegramLoginService.Result.Outcome.FAILED);
    }

    // --- Action id: unknown -------------------------------------------------------------------------------

    @Test
    void login_whenActionIdMatchesNoAction_returnsFailed() {
        var actionId = UUID.randomUUID();
        stubAction(actionId);
        when(actionTokenService.findConfirmEmailActionInfo(actionId)).thenReturn(null);
        when(actionTokenService.findBindTelegramActionInfo(actionId)).thenReturn(null);

        var result = service.login(INIT_DATA, false);

        assertThat(result.outcome()).isEqualTo(TelegramLoginService.Result.Outcome.FAILED);
    }

    // --- helpers ------------------------------------------------------------------------------------------

    private void stubAction(UUID actionId) {
        when(telegramAuthenticationService.findStartParam(INIT_DATA)).thenReturn(actionId.toString());
    }

    private static IdentityAction.ConfirmEmailInfo confirmEmailInfo(UUID id, UniqueId userUniqueId, boolean consent) {
        return IdentityAction.ConfirmEmailInfo.builder()
                .id(id)
                .userUniqueId(userUniqueId)
                .personalInformationConsentGiven(consent)
                .build();
    }

    private static TelegramBotWithUri botWithUrl() {
        return new TelegramBotWithUri(
                url("https://t.me/identityvgbot"),
                TelegramBot.builder().applicationId(APPLICATION_ID).token("bot-token").build()
        );
    }

    private static TelegramUserPrincipal telegramUser(long id, String firstName) {
        return TelegramUserPrincipal.builder()
                .id(id)
                .firstName(firstName)
                .build();
    }

    private static IdentityPrincipalEntity principal(long id) {
        return IdentityPrincipalEntity.builder()
                .uniqueId(id)
                .type(IdentityPrincipalType.USER)
                .build();
    }

    private static IdentityUserEntity userEntity(long id, Instant consentAt) {
        return IdentityUserEntity.builder()
                .uniqueId(id)
                .consentToKeepPersonalDataAt(consentAt)
                .build();
    }

    private static IdentityUser identityUser(long id) {
        return IdentityUser.builder()
                .uniqueId(new UniqueId(id))
                .username("user-" + id)
                .build();
    }

    private static URI url(String value) {
        return URI.create(value);
    }
}
