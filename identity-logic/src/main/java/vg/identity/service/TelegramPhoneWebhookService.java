package vg.identity.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vg.identity.model.TelegramWebhookUpdate;
import vg.identity.model.application.TelegramBot;
import vg.unique.id.model.UniqueId;

/**
 * Processes the small allowlisted part of an untrusted Telegram update needed for phone sharing. Invalid,
 * unsolicited, and unauthenticated updates intentionally have no observable effect. Repeated valid deliveries
 * are safe because phone replacement and membership-flag updates are idempotent.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramPhoneWebhookService {
    private final TelegramWebhookSecretService secretService;
    private final TelegramBotLookupService telegramBotLookupService;
    private final IdentityUserChannelService identityUserChannelService;
    private final IdentityApplicationUserService applicationUserService;
    private final PhoneNumberNormalizer phoneNumberNormalizer;

    @Transactional
    public void accept(UniqueId applicationUniqueId, String webhookSecret, TelegramWebhookUpdate update) {
        if (!secretService.isEnabled() || webhookSecret == null) {
            return;
        }

        final TelegramBot telegramBot;
        try {
            telegramBot = telegramBotLookupService.findTelegramBot(applicationUniqueId);
        } catch (IllegalArgumentException e) {
            return;
        }
        if (telegramBot == null) {
            log.warn("Application with id {} does not exist. Skipping...", applicationUniqueId);
            return;
        }
        if (telegramBot.telegramBotId() == null
                || !secretService.matches(applicationUniqueId, telegramBot.telegramBotId(), webhookSecret)) {
            return;
        }

        if (!isSelfContact(update)) {
            log.warn("Non self contact detected. Skipping...");
            return;
        }

        var normalizedPhone = phoneNumberNormalizer.normalize(update.message().contact().phoneNumber());
        if (normalizedPhone == null) {
            log.warn("Normalized phone number is invalid. Skipping...");
            return;
        }

        var telegramUserId = update.message().from().id();
        var user = identityUserChannelService.findUserByTelegramId(telegramUserId);
        if (user == null || user.getConsentToKeepPersonalDataAt() == null) {
            log.warn("User not found or did not give consent to keep data. Skipping...");
            return;
        }

        if (!identityUserChannelService.recordTelegramPhone(telegramUserId, normalizedPhone)) {
            log.warn("Was not able to record telegram phone. Skipping...");
            return;
        }

        applicationUserService.enableTelegramPhoneDisclosure(applicationUniqueId, user.getUniqueId());
        log.info("Application phone disclosure has been recorded successfully.");
    }

    private boolean isSelfContact(TelegramWebhookUpdate update) {
        if (update == null || update.message() == null
                || update.message().from() == null || update.message().from().id() == null
                || update.message().chat() == null || !"private".equals(update.message().chat().type())
                || update.message().contact() == null || update.message().contact().userId() == null) {
            return false;
        }
        return update.message().from().id().equals(update.message().contact().userId());
    }
}
