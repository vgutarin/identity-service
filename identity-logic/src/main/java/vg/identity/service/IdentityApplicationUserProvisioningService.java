package vg.identity.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vg.identity.entity.IdentityUserEntity;
import vg.identity.model.TelegramUserPrincipal;
import vg.identity.repository.IdentityUserRepository;

import java.time.Clock;
import java.util.Optional;

/** Resolves a verified Telegram identity to one stable identity user, provisioning it when necessary. */
@Service
@RequiredArgsConstructor
public class IdentityApplicationUserProvisioningService {
    private final IdentityUserChannelService userChannelService;
    private final IdentityUserService userService;
    private final IdentityUserRepository userRepository;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    /**
     * Looks up the identity user bound to the given verified Telegram identity <b>without</b> creating or
     * persisting anything. Returns empty when no user is bound yet (consent-before-storage: the caller must
     * not provision until consent is given).
     */
    public Optional<IdentityUserEntity> findTelegramUser(TelegramUserPrincipal telegramUser) {
        return Optional.ofNullable(userChannelService.findUserByTelegramId(telegramUser.id()));
    }

    /**
     * Establishes the identity user for a verified Telegram identity once consent to keep personal data has
     * been given: creates and binds a new user when none exists, or backfills consent on a pre-existing user
     * that lacks it (e.g. a legacy row provisioned before consent gating). Idempotent — an already-established
     * user is returned unchanged. Caller MUST only invoke this after consent is granted.
     */
    public IdentityUserEntity establishTelegramUser(TelegramUserPrincipal telegramUser) {
        var existing = userChannelService.findUserByTelegramId(telegramUser.id());
        if (existing != null) {
            return ensureConsentRecorded(existing);
        }

        try {
            var result = new TransactionTemplate(transactionManager).execute(status -> provisionWithConsent(telegramUser));
            if (result == null) {
                throw new IllegalStateException("Cannot provision identity user for Telegram authentication");
            }
            return result;
        } catch (DataIntegrityViolationException collision) {
            var resolved = userChannelService.findUserByTelegramId(telegramUser.id());
            if (resolved != null) {
                return ensureConsentRecorded(resolved);
            }
            throw collision;
        }
    }

    private IdentityUserEntity provisionWithConsent(TelegramUserPrincipal telegramUser) {
        // Re-check inside the transaction: another request may have provisioned the same Telegram user
        // between the pre-transaction fast-path read and this point.
        var existing = userChannelService.findUserByTelegramId(telegramUser.id());
        if (existing != null) {
            return ensureConsentRecorded(existing);
        }

        var user = ensureConsentRecorded(userService.createAnonymousEntity());
        userChannelService.bindTelegramUser(telegramUser, user);
        return user;
    }

    private IdentityUserEntity ensureConsentRecorded(IdentityUserEntity user) {
        if (user.getConsentToKeepPersonalDataAt() == null) {
            user.setConsentToKeepPersonalDataAt(clock.instant());
            return userRepository.save(user);
        }
        return user;
    }
}
