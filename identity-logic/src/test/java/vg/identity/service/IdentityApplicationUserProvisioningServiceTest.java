package vg.identity.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import vg.identity.entity.IdentityUserEntity;
import vg.identity.model.TelegramUserPrincipal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityApplicationUserProvisioningServiceTest {
    private static final long TELEGRAM_ID = 42L;

    @Mock
    private IdentityUserChannelService userChannelService;
    @Mock
    private IdentityUserService userService;
    @Mock
    private PlatformTransactionManager transactionManager;

    @InjectMocks
    private IdentityApplicationUserProvisioningService service;

    @Test
    void resolveTelegramUser_whenUserAlreadyExists_returnsItWithoutOpeningATransaction() {
        var telegramUser = telegramUser(TELEGRAM_ID);
        var existing = user(7L);
        when(userChannelService.findUserByTelegramId(TELEGRAM_ID)).thenReturn(existing);

        var result = service.resolveTelegramUser(telegramUser);

        assertThat(result).isSameAs(existing);
        // Fast path: a single lookup, no provisioning work, and crucially no transaction is started.
        verify(userChannelService).findUserByTelegramId(TELEGRAM_ID);
        verifyNoMoreInteractions(userChannelService);
        verifyNoInteractions(userService, transactionManager);
    }

    @Test
    void resolveTelegramUser_whenNoUserExists_provisionsAndBindsANewUser() {
        var telegramUser = telegramUser(TELEGRAM_ID);
        var newUser = user(100L);
        // Missed by both the fast-path read and the in-transaction re-check.
        when(userChannelService.findUserByTelegramId(TELEGRAM_ID)).thenReturn(null);
        when(userService.createAnonymousEntity()).thenReturn(newUser);

        var result = service.resolveTelegramUser(telegramUser);

        assertThat(result).isSameAs(newUser);
        verify(userChannelService, times(2)).findUserByTelegramId(TELEGRAM_ID);
        verify(userService).createAnonymousEntity();
        verify(userChannelService).bindTelegramUser(telegramUser, newUser);
    }

    @Test
    void resolveTelegramUser_whenAnotherRequestProvisionsConcurrently_returnsThatUserWithoutCreatingAnother() {
        var telegramUser = telegramUser(TELEGRAM_ID);
        var concurrentlyCreated = user(55L);
        when(userChannelService.findUserByTelegramId(TELEGRAM_ID))
                .thenReturn(null)                 // pre-transaction fast path misses
                .thenReturn(concurrentlyCreated); // in-transaction re-check now sees the winner's row

        var result = service.resolveTelegramUser(telegramUser);

        assertThat(result).isSameAs(concurrentlyCreated);
        verify(userChannelService, never()).bindTelegramUser(any(), any());
        verifyNoInteractions(userService);
    }

    @Test
    void resolveTelegramUser_whenConcurrentInsertCollides_recoversByReReadingTheWinnersUser() {
        var telegramUser = telegramUser(TELEGRAM_ID);
        var newUser = user(100L);
        var winnersUser = user(77L);
        when(userChannelService.findUserByTelegramId(TELEGRAM_ID))
                .thenReturn(null, null, winnersUser); // fast path, in-tx re-check, post-collision recovery
        when(userService.createAnonymousEntity()).thenReturn(newUser);
        doThrow(new DataIntegrityViolationException("duplicate telegram channel"))
                .when(userChannelService).bindTelegramUser(telegramUser, newUser);

        var result = service.resolveTelegramUser(telegramUser);

        assertThat(result).isSameAs(winnersUser);
        verify(userChannelService, times(3)).findUserByTelegramId(TELEGRAM_ID);
    }

    @Test
    void resolveTelegramUser_whenCollisionCannotBeRecovered_rethrowsTheDataIntegrityViolation() {
        var telegramUser = telegramUser(TELEGRAM_ID);
        var newUser = user(100L);
        var collision = new DataIntegrityViolationException("duplicate telegram channel");
        when(userChannelService.findUserByTelegramId(TELEGRAM_ID)).thenReturn(null); // all three reads miss
        when(userService.createAnonymousEntity()).thenReturn(newUser);
        doThrow(collision).when(userChannelService).bindTelegramUser(telegramUser, newUser);

        assertThatThrownBy(() -> service.resolveTelegramUser(telegramUser)).isSameAs(collision);
        verify(userChannelService, times(3)).findUserByTelegramId(TELEGRAM_ID);
    }

    private static IdentityUserEntity user(long uniqueId) {
        return IdentityUserEntity.builder()
                .uniqueId(uniqueId)
                .build();
    }

    private static TelegramUserPrincipal telegramUser(long id) {
        return TelegramUserPrincipal.builder()
                .id(id)
                .firstName("John")
                .build();
    }
}
