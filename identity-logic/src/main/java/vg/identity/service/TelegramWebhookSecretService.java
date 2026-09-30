package vg.identity.service;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import vg.unique.id.model.UniqueId;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.regex.Pattern;

/** Derives stable application-and-bot-bound Telegram webhook secrets from operator-provided master secrets. */
@Component
public class TelegramWebhookSecretService {
    private static final String HMAC_SHA_256 = "HmacSHA256";
    private static final Pattern TELEGRAM_SECRET_PATTERN = Pattern.compile("[A-Za-z0-9_-]{32,256}");

    private final String masterSecret;
    private final String previousMasterSecret;
    @Getter
    private final boolean enabled;

    public TelegramWebhookSecretService(
            @Value("${identity.telegram.webhook.secret:}") String masterSecret,
            @Value("${identity.telegram.webhook.previous-secret:}") String previousMasterSecret,
            @Value("${identity.telegram.webhook.enabled:false}") boolean enabled
    ) {
        this.masterSecret = masterSecret;
        this.previousMasterSecret = previousMasterSecret;
        this.enabled = enabled;
        if (enabled && !hasValidSecret(masterSecret)) {
            throw new IllegalStateException(
                    "identity.telegram.webhook.secret must contain 32-256 characters using only A-Z, a-z, 0-9, underscore, or hyphen when identity.telegram.webhook.enabled=true"
            );
        }
        if (enabled && StringUtils.hasLength(previousMasterSecret) && !hasValidSecret(previousMasterSecret)) {
            throw new IllegalStateException(
                    "identity.telegram.webhook.previous-secret must contain 32-256 characters using only A-Z, a-z, 0-9, underscore, or hyphen when configured"
            );
        }
    }

    public String secretFor(UniqueId applicationUniqueId, long telegramBotId) {
        if (!enabled) {
            throw new IllegalStateException("identity.telegram.webhook.enabled is false");
        }
        if (telegramBotId <= 0) {
            throw new IllegalArgumentException("telegramBotId must be positive");
        }
        return derive(masterSecret, applicationUniqueId, telegramBotId);
    }

    private String derive(String secret, UniqueId applicationUniqueId, long telegramBotId) {
        try {
            var mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA_256));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal((applicationUniqueId + ":" + telegramBotId).getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception e) {
            throw new IllegalStateException("Cannot derive Telegram webhook secret", e);
        }
    }

    public boolean matches(UniqueId applicationUniqueId, long telegramBotId, String providedSecret) {
        if (!enabled || providedSecret == null || telegramBotId <= 0) {
            return false;
        }
        var providedBytes = providedSecret.getBytes(StandardCharsets.UTF_8);
        var currentMatches = MessageDigest.isEqual(secretFor(applicationUniqueId, telegramBotId).getBytes(StandardCharsets.UTF_8), providedBytes);
        var previousMatches = StringUtils.hasLength(previousMasterSecret)
                && MessageDigest.isEqual(derive(previousMasterSecret, applicationUniqueId, telegramBotId).getBytes(StandardCharsets.UTF_8), providedBytes);
        return currentMatches || previousMatches;
    }

    private boolean hasValidSecret(String secret) {
        return secret != null && TELEGRAM_SECRET_PATTERN.matcher(secret).matches();
    }
}
