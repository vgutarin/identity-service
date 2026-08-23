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
import vg.identity.repository.IdentityUserRepository;

import java.time.Clock;
import java.time.Instant;

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
    private static final Instant NOW = Instant.parse("2026-08-23T00:00:00Z");

    @Mock
    private IdentityUserChannelService userChannelService;
    @Mock
    private IdentityUserService userService;
    @Mock
    private IdentityUserRepository userRepository;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private Clock clock;

    @InjectMocks
    private IdentityApplicationUserProvisioningService service;

    @Test
    void establishTelegramUser_whenAnotherRequestProvisionsConcurrently_returnsThatUserWithoutCreatingAnother() {
        var telegramUser = telegramUser(TELEGRAM_ID);
        var concurrentlyCreated = userWithConsent(55L, NOW); // the winner already established consent
        when(userChannelService.findUserByTelegramId(TELEGRAM_ID))
                .thenReturn(null)                 // pre-transaction fast path misses
                .thenReturn(concurrentlyCreated); // in-transaction re-check now sees the winner's row

        var result = service.establishTelegramUser(telegramUser);

        assertThat(result).isSameAs(concurrentlyCreated);
        verify(userChannelService, never()).bindTelegramUser(any(), any());
        verifyNoInteractions(userService, userRepository);
    }

    @Test
    void establishTelegramUser_whenConcurrentInsertCollides_recoversByReReadingTheWinnersUser() {
        var telegramUser = telegramUser(TELEGRAM_ID);
        var newUser = user(100L);
        var winnersUser = userWithConsent(77L, NOW);
        when(userChannelService.findUserByTelegramId(TELEGRAM_ID))
                .thenReturn(null, null, winnersUser); // fast path, in-tx re-check, post-collision recovery
        when(userService.createAnonymousEntity()).thenReturn(newUser);
        when(clock.instant()).thenReturn(NOW);
        when(userRepository.save(newUser)).thenReturn(newUser);
        doThrow(new DataIntegrityViolationException("duplicate telegram channel"))
                .when(userChannelService).bindTelegramUser(telegramUser, newUser);

        var result = service.establishTelegramUser(telegramUser);

        assertThat(result).isSameAs(winnersUser);
        verify(userChannelService, times(3)).findUserByTelegramId(TELEGRAM_ID);
    }

    @Test
    void establishTelegramUser_whenCollisionCannotBeRecovered_rethrowsTheDataIntegrityViolation() {
        var telegramUser = telegramUser(TELEGRAM_ID);
        var newUser = user(100L);
        var collision = new DataIntegrityViolationException("duplicate telegram channel");
        when(userChannelService.findUserByTelegramId(TELEGRAM_ID)).thenReturn(null); // all three reads miss
        when(userService.createAnonymousEntity()).thenReturn(newUser);
        when(clock.instant()).thenReturn(NOW);
        when(userRepository.save(newUser)).thenReturn(newUser);
        doThrow(collision).when(userChannelService).bindTelegramUser(telegramUser, newUser);

        assertThatThrownBy(() -> service.establishTelegramUser(telegramUser)).isSameAs(collision);
        verify(userChannelService, times(3)).findUserByTelegramId(TELEGRAM_ID);
    }

    @Test
    void findTelegramUser_whenBound_returnsItWithoutCreatingOrOpeningATransaction() {
        var telegramUser = telegramUser(TELEGRAM_ID);
        var existing = user(7L);
        when(userChannelService.findUserByTelegramId(TELEGRAM_ID)).thenReturn(existing);

        var result = service.findTelegramUser(telegramUser);

        assertThat(result).contains(existing);
        verify(userChannelService).findUserByTelegramId(TELEGRAM_ID);
        verifyNoMoreInteractions(userChannelService);
        verifyNoInteractions(userService, transactionManager);
    }

    @Test
    void findTelegramUser_whenNotBound_returnsEmptyAndCreatesNothing() {
        var telegramUser = telegramUser(TELEGRAM_ID);
        when(userChannelService.findUserByTelegramId(TELEGRAM_ID)).thenReturn(null);

        var result = service.findTelegramUser(telegramUser);

        assertThat(result).isEmpty();
        verify(userChannelService).findUserByTelegramId(TELEGRAM_ID);
        verify(userChannelService, never()).bindTelegramUser(any(), any());
        verifyNoInteractions(userService, transactionManager);
    }

    @Test
    void establishTelegramUser_whenNoUser_createsBindsAndRecordsConsent() {
        var telegramUser = telegramUser(TELEGRAM_ID);
        var newUser = user(100L); // consent not yet recorded
        when(userChannelService.findUserByTelegramId(TELEGRAM_ID)).thenReturn(null);
        when(userService.createAnonymousEntity()).thenReturn(newUser);
        when(clock.instant()).thenReturn(NOW);
        when(userRepository.save(newUser)).thenReturn(newUser);

        var result = service.establishTelegramUser(telegramUser);

        assertThat(result).isSameAs(newUser);
        assertThat(newUser.getConsentToKeepPersonalDataAt()).isEqualTo(NOW);
        verify(userRepository).save(newUser);
        verify(userChannelService).bindTelegramUser(telegramUser, newUser);
    }

    @Test
    void establishTelegramUser_whenUserAlreadyConsented_returnsItWithoutWritingOrCreating() {
        var telegramUser = telegramUser(TELEGRAM_ID);
        var consented = userWithConsent(7L, NOW);
        when(userChannelService.findUserByTelegramId(TELEGRAM_ID)).thenReturn(consented);

        var result = service.establishTelegramUser(telegramUser);

        assertThat(result).isSameAs(consented);
        verify(userChannelService).findUserByTelegramId(TELEGRAM_ID);
        verifyNoMoreInteractions(userChannelService);
        verifyNoInteractions(userService, userRepository, transactionManager);
    }

    @Test
    void establishTelegramUser_whenLegacyUserLacksConsent_backfillsConsentWithoutCreating() {
        var telegramUser = telegramUser(TELEGRAM_ID);
        var legacy = user(7L); // exists but consent never recorded
        when(userChannelService.findUserByTelegramId(TELEGRAM_ID)).thenReturn(legacy);
        when(clock.instant()).thenReturn(NOW);
        when(userRepository.save(legacy)).thenReturn(legacy);

        var result = service.establishTelegramUser(telegramUser);

        assertThat(result).isSameAs(legacy);
        assertThat(legacy.getConsentToKeepPersonalDataAt()).isEqualTo(NOW);
        verify(userRepository).save(legacy);
        verify(userChannelService, never()).bindTelegramUser(any(), any());
        verifyNoInteractions(userService, transactionManager);
    }

    private static IdentityUserEntity user(long uniqueId) {
        return IdentityUserEntity.builder()
                .uniqueId(uniqueId)
                .build();
    }

    private static IdentityUserEntity userWithConsent(long uniqueId, Instant consentAt) {
        return IdentityUserEntity.builder()
                .uniqueId(uniqueId)
                .consentToKeepPersonalDataAt(consentAt)
                .build();
    }

    private static TelegramUserPrincipal telegramUser(long id) {
        return TelegramUserPrincipal.builder()
                .id(id)
                .firstName("John")
                .build();
    }
}
