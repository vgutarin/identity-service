package vg.identity.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vg.unique.id.model.UniqueId;

/**
 * Emits security-audit records for consent-gated Telegram authentication (constitution Principle V).
 *
 * <p>Records are written through the dedicated {@code vg.identity.security.audit} logger — route it to an
 * append-only sink for tamper-evidence. Users are referenced by their stable subject ({@code sub}) only; the
 * raw Telegram init data, Telegram profile fields, and any credential/token are never logged. Logged values
 * are sanitized against log injection.</p>
 */
@Component
public class TelegramAuthenticationAuditor {

    private static final Logger AUDIT = LoggerFactory.getLogger("vg.identity.security.audit");

    /** Records that consent was given and a brand-new identity user was created for a Telegram identity. */
    public void userProvisioned(UniqueId applicationUniqueId, long identityUserUniqueId) {
        AUDIT.info("event=telegram.user.provision outcome=CREATED applicationUniqueId={} sub={}",
                sanitize(applicationUniqueId.toString()),
                sanitize(new UniqueId(identityUserUniqueId).toString()));
    }

    /** Records a successful Telegram authentication resolving to an established user. */
    public void authenticationSucceeded(UniqueId applicationUniqueId, long identityUserUniqueId) {
        AUDIT.info("event=telegram.auth outcome=SUCCESS applicationUniqueId={} sub={}",
                sanitize(applicationUniqueId.toString()),
                sanitize(new UniqueId(identityUserUniqueId).toString()));
    }

    private static String sanitize(String value) {
        return value == null ? "none" : value.replaceAll("[\\p{Cntrl}\\s]", "_");
    }
}
