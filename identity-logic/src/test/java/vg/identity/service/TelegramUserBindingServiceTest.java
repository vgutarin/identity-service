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
import vg.identity.model.TelegramUserPrincipal;
import vg.identity.model.application.TelegramBot;
import vg.identity.repository.IdentityUserRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramUserBindingServiceTest {

    @Mock
    private IdentityActionTokenService actionTokenService;
    @Mock
    private TelegramAuthenticationService telegramAuthenticationService;
    @Mock
    private IdentityUserChannelService channelService;
    @Mock
    private IdentityUserRepository userRepository;

    private TelegramUserBindingService service;

    @BeforeEach
    void setUp() {
        service = new TelegramUserBindingService(
                actionTokenService,
                telegramAuthenticationService,
                channelService,
                userRepository
        );
    }

    @Test
    void bind_whenStartParamIsNotAnActionId_returnsInvalidAction() {
        when(telegramAuthenticationService.findStartParam("init-data")).thenReturn("not-a-uuid");

        assertThat(service.bind("init-data"))
                .isEqualTo(new TelegramUserBindingService.Result(false, null));

        verify(actionTokenService, never()).findBindTelegramActionInfo(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void bind_whenTelegramDataIsInvalid_doesNotAttachChannelOrConsumeAction() {
        var actionId = UUID.randomUUID();
        var actionInfo = actionInfo(actionId, principal(17L));
        when(telegramAuthenticationService.findStartParam("init-data")).thenReturn(actionId.toString());
        when(actionTokenService.findBindTelegramActionInfo(actionId)).thenReturn(actionInfo);
        when(telegramAuthenticationService.parseUser(actionInfo.telegramBot(), "init-data")).thenReturn(Optional.empty());

        assertThat(service.bind("init-data"))
                .isEqualTo(new TelegramUserBindingService.Result(false, null));

        verify(channelService, never()).bindTelegramUser(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(actionTokenService, never()).consumeBindTelegramAction(actionId);
    }

    @Test
    void bind_whenTelegramChannelIsAttachedSuccessfully_consumesAction() {
        var actionId = UUID.randomUUID();
        var principal = principal(17L);
        var actionInfo = actionInfo(actionId, principal);
        var user = IdentityUserEntity.builder().uniqueId(17L).build();
        var telegramUser = TelegramUserPrincipal.builder().id(42L).build();
        when(telegramAuthenticationService.findStartParam("init-data")).thenReturn(actionId.toString());
        when(actionTokenService.findBindTelegramActionInfo(actionId)).thenReturn(actionInfo);
        when(telegramAuthenticationService.parseUser(actionInfo.telegramBot(), "init-data"))
                .thenReturn(Optional.of(telegramUser));
        when(userRepository.findById(17L)).thenReturn(Optional.of(user));
        when(channelService.bindTelegramUser(telegramUser, user))
                .thenReturn(IdentityUserChannelService.TelegramBindResult.SUCCESS);

        assertThat(service.bind("init-data"))
                .isEqualTo(new TelegramUserBindingService.Result(true, telegramUser));

        verify(actionTokenService).consumeBindTelegramAction(actionId);
    }

    @Test
    void bind_whenTelegramChannelBelongsToAnotherUser_doesNotConsumeAction() {
        var actionId = UUID.randomUUID();
        var principal = principal(17L);
        var actionInfo = actionInfo(actionId, principal);
        var user = IdentityUserEntity.builder().uniqueId(17L).build();
        var telegramUser = TelegramUserPrincipal.builder().id(42L).build();
        when(telegramAuthenticationService.findStartParam("init-data")).thenReturn(actionId.toString());
        when(actionTokenService.findBindTelegramActionInfo(actionId)).thenReturn(actionInfo);
        when(telegramAuthenticationService.parseUser(actionInfo.telegramBot(), "init-data"))
                .thenReturn(Optional.of(telegramUser));
        when(userRepository.findById(17L)).thenReturn(Optional.of(user));
        when(channelService.bindTelegramUser(telegramUser, user))
                .thenReturn(IdentityUserChannelService.TelegramBindResult.CHANNEL_ATTACHED_TO_ANOTHER_USER);

        assertThat(service.bind("init-data"))
                .isEqualTo(new TelegramUserBindingService.Result(false, null));

        verify(actionTokenService, never()).consumeBindTelegramAction(actionId);
    }

    private static IdentityAction.BindTelegramInfo actionInfo(UUID actionId, IdentityPrincipalEntity principal) {
        return new IdentityAction.BindTelegramInfo(actionId, TelegramBot.builder().token("token").build(), principal);
    }

    private static IdentityPrincipalEntity principal(long id) {
        return IdentityPrincipalEntity.builder()
                .uniqueId(id)
                .type(IdentityPrincipalType.USER)
                .build();
    }
}
