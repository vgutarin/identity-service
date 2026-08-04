package vg.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vg.identity.entity.IdentityActionTokenEntity;
import vg.identity.entity.IdentityUserChannelEntity;
import vg.identity.entity.IdentityUserEntity;
import vg.identity.model.IdentityActionType;
import vg.identity.model.IdentityChannelType;
import vg.identity.model.TelegramUserPrincipal;
import vg.identity.model.UserProvisioningDetails;
import vg.identity.repository.IdentityUserChannelRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityActionTokenProcessorServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-30T10:00:00Z");
    private static final String ACTION_KEY = "7_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @Mock private IdentityUserChannelRepository channelRepository;
    @Mock private IdentityUserService userService;
    @Mock private IdentityUserChannelService channelService;
    @Mock private IdentityActionTokenService actionTokenService;

    private IdentityActionTokenProcessorService service;

    @BeforeEach
    void setUp() {
        service = new IdentityActionTokenProcessorService(
                channelRepository, userService, channelService, actionTokenService, Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void confirmEmail_whenPendingChannelExists_createsAndAttachesUserThenConsumesAction() {
        var channel = emailChannel(null);
        var user = user(17L);
        when(actionTokenService.findConfirmEmailActionForUpdate(ACTION_KEY)).thenReturn(verification(channel));
        when(userService.getOrCreateEntityForEmailChannel("john@example.com", null)).thenReturn(user);

        var result = service.confirmEmail(ACTION_KEY);

        assertThat(result.success()).isTrue();
        assertThat(channel.getVerifiedAt()).isEqualTo(NOW);
        assertThat(user.getConsentToKeepPersonalDataAt()).isEqualTo(NOW);
        verify(channelService).attachUser(channel, user);
        verify(actionTokenService).consumeAction(7L);
    }

    @Test
    void confirmEmail_whenProvisioningSupplied_forwardsItToUserProvisioning() {
        var channel = emailChannel(null);
        var user = user(17L);
        var provisioning = new UserProvisioningDetails("John Doe", "Abcdefghi1", true);
        when(actionTokenService.findConfirmEmailActionForUpdate(ACTION_KEY)).thenReturn(verification(channel));
        when(userService.getOrCreateEntityForEmailChannel("john@example.com", provisioning)).thenReturn(user);

        assertThat(service.confirmEmail(ACTION_KEY, provisioning).success()).isTrue();

        verify(userService).getOrCreateEntityForEmailChannel("john@example.com", provisioning);
        verify(actionTokenService).consumeAction(7L);
    }

    @Test
    void confirmEmail_whenEmailChannelAlreadyHasUser_reusesVerifiesAndConsumesItWithoutProvisioningAnother() {
        var user = user(17L);
        var channel = emailChannel(user);
        when(actionTokenService.findConfirmEmailActionForUpdate(ACTION_KEY)).thenReturn(verification(channel));

        assertThat(service.confirmEmail(ACTION_KEY).success()).isTrue();

        verify(userService, never()).findEntityByUsername(any());
        verify(userService, never()).getOrCreateEntityForEmailChannel(any(), any());
        verify(channelService).attachUser(channel, user);
        assertThat(channel.getVerifiedAt()).isEqualTo(NOW);
        assertThat(user.getConsentToKeepPersonalDataAt()).isEqualTo(NOW);
        verify(channelRepository).save(channel);
        verify(channelRepository).flush();
        verify(actionTokenService).consumeAction(7L);
    }

    @Test
    void confirmEmail_whenUserAlreadyConsented_doesNotOverwriteConsent() {
        var existingConsent = NOW.minusSeconds(3600);
        var user = IdentityUserEntity.builder()
                .uniqueId(17L)
                .consentToKeepPersonalDataAt(existingConsent)
                .build();
        var channel = emailChannel(user);
        when(actionTokenService.findConfirmEmailActionForUpdate(ACTION_KEY)).thenReturn(verification(channel));

        assertThat(service.confirmEmail(ACTION_KEY).success()).isTrue();

        assertThat(channel.getVerifiedAt()).isEqualTo(NOW);
        assertThat(user.getConsentToKeepPersonalDataAt()).isEqualTo(existingConsent);
        verify(actionTokenService).consumeAction(7L);
    }

    @Test
    void confirmEmailWithTelegram_whenEmailAndTelegramBelongToDifferentUsers_returnsNullWithoutMutating() {
        var emailChannel = emailChannel(user(17L));
        when(actionTokenService.findConfirmEmailActionForUpdate(ACTION_KEY)).thenReturn(verification(emailChannel));
        when(channelService.findUserByTelegramId(42L)).thenReturn(user(18L));

        assertThat(service.confirmEmailWithTelegram(ACTION_KEY, TelegramUserPrincipal.builder().id(42L).build())).isNull();

        assertThat(emailChannel.getVerifiedAt()).isNull();
        verify(channelService, never()).attachUser(any(IdentityUserChannelEntity.class), any());
        verify(channelService, never()).bindTelegramUser(any(), any());
        verify(actionTokenService, never()).consumeAction(any());
    }

    @Test
    void confirmEmailWithTelegram_whenCredentialsRequired_throwsWithoutMutation() {
        var channel = emailChannel(null);
        when(actionTokenService.findConfirmEmailActionForUpdate(ACTION_KEY)).thenReturn(verification(channel));
        when(channelService.findUserByTelegramId(42L)).thenReturn(null);

        assertThatThrownBy(() -> service.confirmEmailWithTelegram(ACTION_KEY, TelegramUserPrincipal.builder().id(42L).build()))
                .isInstanceOf(IdentityActionTokenProcessorService.CredentialsRequiredException.class);

        verify(channelService, never()).attachUser(any(IdentityUserChannelEntity.class), any());
        verify(actionTokenService, never()).consumeAction(any());
    }

    @Test
    void confirmEmailWithTelegram_whenBrandNewUserWithCredentials_provisionsAttachesVerifiesAndBinds() {
        var channel = emailChannel(null);
        var user = user(17L);
        var telegram = TelegramUserPrincipal.builder().id(42L).build();
        var provisioning = new UserProvisioningDetails("John Doe", "Abcdefghi1", true);
        when(actionTokenService.findConfirmEmailActionForUpdate(ACTION_KEY)).thenReturn(verification(channel));
        when(channelService.findUserByTelegramId(42L)).thenReturn(null);
        when(userService.getOrCreateEntityForEmailChannel("john@example.com", provisioning)).thenReturn(user);
        when(channelService.bindTelegramUser(telegram, user))
                .thenReturn(IdentityUserChannelService.TelegramBindResult.SUCCESS);

        assertThat(service.confirmEmailWithTelegram(ACTION_KEY, telegram, provisioning)).isSameAs(user);

        assertThat(channel.getVerifiedAt()).isEqualTo(NOW);
        assertThat(user.getConsentToKeepPersonalDataAt()).isEqualTo(NOW);
        verify(channelService).attachUser(channel, user);
        verify(channelService).bindTelegramUser(telegram, user);
        verify(actionTokenService).consumeAction(7L);
    }

    private static IdentityActionTokenEntity verification(IdentityUserChannelEntity channel) {
        return IdentityActionTokenEntity.builder()
                .id(7L)
                .actionType(IdentityActionType.CONFIRM_EMAIL)
                .identityUserChannel(channel)
                .createdAt(NOW.minusSeconds(60))
                .expireAt(NOW.plusSeconds(60))
                .build();
    }

    private static IdentityUserChannelEntity emailChannel(IdentityUserEntity user) {
        return IdentityUserChannelEntity.builder()
                .uniqueId(7L)
                .channelType(IdentityChannelType.EMAIL)
                .channelUserId("john@example.com")
                .identityUser(user)
                .build();
    }

    private static IdentityUserEntity user(long uniqueId) {
        return IdentityUserEntity.builder().uniqueId(uniqueId).build();
    }
}
