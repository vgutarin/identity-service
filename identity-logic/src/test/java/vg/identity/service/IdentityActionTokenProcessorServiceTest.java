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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityActionTokenProcessorServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-30T10:00:00Z");

    @Mock
    private IdentityUserChannelRepository channelRepository;
    @Mock
    private IdentityUserService userService;
    @Mock
    private IdentityUserChannelService channelService;
    @Mock
    private IdentityActionTokenService actionTokenService;

    private IdentityActionTokenProcessorService service;

    @BeforeEach
    void setUp() {
        service = new IdentityActionTokenProcessorService(
                channelRepository,
                userService,
                channelService,
                actionTokenService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void confirmEmail_whenPendingChannelExists_createsAndAttachesUserThenConsumesAction() {
        var id = UUID.randomUUID();
        var channel = emailChannel(null);
        var user = user(17L);
        var verification = verification(id, channel);
        when(actionTokenService.findConfirmEmailActionForUpdate(id)).thenReturn(verification);
        when(userService.getOrCreateEntityForEmailChannel("john@example.com", null)).thenReturn(user);

        var result = service.confirmEmail(id);

        assertThat(result.success()).isTrue();
        assertThat(result.bindTelegramUrl()).isNull();
        assertThat(channel.getVerifiedAt()).isEqualTo(NOW);
        assertThat(user.getConsentToKeepPersonalDataAt()).isEqualTo(NOW);
        verify(channelService).attachUser(channel, user);
        verify(channelRepository).save(channel);
        verify(channelRepository).flush();
        verify(actionTokenService).consumeAction(id);
    }

    @Test
    void confirmEmail_whenProvisioningSupplied_forwardsItToUserProvisioning() {
        var id = UUID.randomUUID();
        var channel = emailChannel(null);
        var user = user(17L);
        var provisioning = new UserProvisioningDetails("John Doe", "Abcdefghi1", true);
        when(actionTokenService.findConfirmEmailActionForUpdate(id)).thenReturn(verification(id, channel));
        when(userService.getOrCreateEntityForEmailChannel("john@example.com", provisioning)).thenReturn(user);

        assertThat(service.confirmEmail(id, provisioning).success()).isTrue();

        verify(userService).getOrCreateEntityForEmailChannel("john@example.com", provisioning);
        verify(channelService).attachUser(channel, user);
        verify(actionTokenService).consumeAction(id);
    }

    @Test
    void confirmEmail_whenEmailChannelAlreadyHasUser_reusesVerifiesAndConsumesItWithoutProvisioningAnother() {
        var id = UUID.randomUUID();
        var user = user(17L);
        var channel = emailChannel(user);
        when(actionTokenService.findConfirmEmailActionForUpdate(id)).thenReturn(verification(id, channel));

        assertThat(service.confirmEmail(id).success()).isTrue();

        verify(userService, never()).findEntityByUsername(any());
        verify(userService, never()).getOrCreateEntityForEmailChannel(any(), any());
        verify(channelService).attachUser(channel, user);
        assertThat(channel.getVerifiedAt()).isEqualTo(NOW);
        assertThat(user.getConsentToKeepPersonalDataAt()).isEqualTo(NOW);
        verify(channelRepository).save(channel);
        verify(channelRepository).flush();
        verify(actionTokenService).consumeAction(id);
    }

    @Test
    void confirmEmail_whenUserAlreadyConsented_doesNotOverwriteConsent() {
        var id = UUID.randomUUID();
        var existingConsent = NOW.minusSeconds(3600);
        var user = IdentityUserEntity.builder()
                .uniqueId(17L)
                .consentToKeepPersonalDataAt(existingConsent)
                .build();
        var channel = emailChannel(user);
        when(actionTokenService.findConfirmEmailActionForUpdate(id)).thenReturn(verification(id, channel));

        assertThat(service.confirmEmail(id).success()).isTrue();

        assertThat(channel.getVerifiedAt()).isEqualTo(NOW);
        assertThat(user.getConsentToKeepPersonalDataAt()).isEqualTo(existingConsent);
        verify(actionTokenService).consumeAction(id);
    }

    @Test
    void confirmEmailWithTelegram_whenEmailAndTelegramBelongToDifferentUsers_returnsNullWithoutMutating() {
        var id = UUID.randomUUID();
        var emailUser = user(17L);
        var telegramUser = user(18L);
        var emailChannel = emailChannel(emailUser);
        var telegram = TelegramUserPrincipal.builder().id(42L).build();
        when(actionTokenService.findConfirmEmailActionForUpdate(id)).thenReturn(verification(id, emailChannel));
        when(channelService.findUserByTelegramId(42L)).thenReturn(telegramUser);

        assertThat(service.confirmEmailWithTelegram(id, telegram)).isNull();

        assertThat(emailChannel.getVerifiedAt()).isNull();
        verify(channelService, never()).attachUser(any(IdentityUserChannelEntity.class), any());
        verify(channelService, never()).bindTelegramUser(any(), any());
        verify(actionTokenService, never()).consumeAction(any());
    }

    @Test
    void confirmEmailWithTelegram_whenBrandNewUserAndNoCredentials_throwsCredentialsRequiredWithoutMutating() {
        var id = UUID.randomUUID();
        var channel = emailChannel(null);
        var telegram = TelegramUserPrincipal.builder().id(42L).build();
        when(actionTokenService.findConfirmEmailActionForUpdate(id)).thenReturn(verification(id, channel));
        when(channelService.findUserByTelegramId(42L)).thenReturn(null);

        assertThatThrownBy(() -> service.confirmEmailWithTelegram(id, telegram))
                .isInstanceOf(IdentityActionTokenProcessorService.CredentialsRequiredException.class);

        assertThat(channel.getVerifiedAt()).isNull();
        verify(userService, never()).getOrCreateEntityForEmailChannel(any(), any());
        verify(channelService, never()).attachUser(any(IdentityUserChannelEntity.class), any());
        verify(channelService, never()).bindTelegramUser(any(), any());
        verify(actionTokenService, never()).consumeAction(any());
    }

    @Test
    void confirmEmailWithTelegram_whenBrandNewUserWithCredentials_provisionsAttachesVerifiesAndBinds() {
        var id = UUID.randomUUID();
        var channel = emailChannel(null);
        var user = user(17L);
        var telegram = TelegramUserPrincipal.builder().id(42L).build();
        var provisioning = new UserProvisioningDetails("John Doe", "Abcdefghi1", true);
        when(actionTokenService.findConfirmEmailActionForUpdate(id)).thenReturn(verification(id, channel));
        when(channelService.findUserByTelegramId(42L)).thenReturn(null);
        when(userService.getOrCreateEntityForEmailChannel("john@example.com", provisioning)).thenReturn(user);
        when(channelService.bindTelegramUser(telegram, user))
                .thenReturn(IdentityUserChannelService.TelegramBindResult.SUCCESS);

        assertThat(service.confirmEmailWithTelegram(id, telegram, provisioning)).isSameAs(user);

        assertThat(channel.getVerifiedAt()).isEqualTo(NOW);
        assertThat(user.getConsentToKeepPersonalDataAt()).isEqualTo(NOW);
        verify(channelService).attachUser(channel, user);
        verify(channelService).bindTelegramUser(telegram, user);
        verify(actionTokenService).consumeAction(id);
    }

    private static IdentityActionTokenEntity verification(UUID id, IdentityUserChannelEntity channel) {
        return IdentityActionTokenEntity.builder()
                .id(id)
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
