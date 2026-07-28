package vg.identity.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vg.identity.entity.IdentityUserEntity;
import vg.identity.model.TelegramUserPrincipal;

/** Resolves a verified Telegram identity to one stable identity user, provisioning it when necessary. */
@Service
@RequiredArgsConstructor
public class IdentityApplicationUserProvisioningService {
    private final IdentityUserChannelService userChannelService;
    private final IdentityUserService userService;
    private final PlatformTransactionManager transactionManager;

    public IdentityUserEntity resolveTelegramUser(TelegramUserPrincipal telegramUser) {
        var existing = userChannelService.findUserByTelegramId(telegramUser.id());
        if (existing != null) {
            return existing;
        }

        try {
            var result = new TransactionTemplate(transactionManager).execute(status -> provisionOrResolve(telegramUser));
            if (result == null) {
                throw new IllegalStateException("Cannot provision identity user for Telegram authentication");
            }
            return result;
        } catch (DataIntegrityViolationException collision) {
            var resolved = userChannelService.findUserByTelegramId(telegramUser.id());
            if (resolved != null) {
                return resolved;
            }
            throw collision;
        }
    }

    private IdentityUserEntity provisionOrResolve(TelegramUserPrincipal telegramUser) {
        // Re-check inside the transaction: another request may have provisioned the same Telegram user
        // between the pre-transaction fast-path read and this point.
        var existing = userChannelService.findUserByTelegramId(telegramUser.id());
        if (existing != null) {
            return existing;
        }

        var user = userService.createAnonymousEntity();
        userChannelService.bindTelegramUser(telegramUser, user);
        return user;
    }
}
