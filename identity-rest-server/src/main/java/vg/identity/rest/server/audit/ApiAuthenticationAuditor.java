package vg.identity.rest.server.audit;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vg.identity.rest.server.IdentityRestApiProperties;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

/**
 * Emits security-audit records for REST API authentication (constitution Principle V).
 *
 * <p>Authentication <em>failures</em> are logged per-event (durable, forensic); <em>successes</em> are
 * counted and flushed periodically (see {@code identity.rest.api.audit.flush-interval}, default 10m) to keep
 * the high-volume success path from flooding the log. Records are written through the dedicated
 * {@code vg.identity.security.audit} logger — route it to an append-only sink for tamper-evidence.</p>
 *
 * <p>The API key value is never logged. On a failed attempt that supplied a value, only the key id (a UUID,
 * not a credential) is recorded. All logged, request-derived values are sanitized against log injection.</p>
 */
@Component
@ConditionalOnBooleanProperty("identity.rest.api.enabled")
public class ApiAuthenticationAuditor {

    private static final Logger AUDIT = LoggerFactory.getLogger("vg.identity.security.audit");
    private static final AuditKey OVERFLOW_KEY = new AuditKey("__overflow__", "", "");
    private static final int MAX_FIELD_LENGTH = 256;

    private final Clock clock;
    private final int maxCounterKeys;
    private final ConcurrentHashMap<AuditKey, LongAdder> successCounters = new ConcurrentHashMap<>();
    private final AtomicReference<Instant> windowStart;
    private final AtomicBoolean overflowWarned = new AtomicBoolean(false);

    public ApiAuthenticationAuditor(Clock clock, IdentityRestApiProperties properties) {
        this.clock = clock;
        this.maxCounterKeys = properties.getAudit().getMaxCounterKeys();
        this.windowStart = new AtomicReference<>(clock.instant());
    }

    /**
     * Records a per-event authentication failure immediately.
     *
     * @param reason     one of {@code missing_header}, {@code multiple_headers}, {@code invalid_key}
     * @param keyId      the key id (UUID) if a value was supplied and parseable, otherwise {@code null}
     */
    public void failure(String reason, String method, String path, String remoteAddr, String keyId) {
        AUDIT.warn("event=api.auth outcome=FAILURE reason={} method={} path={} remoteAddr={} keyId={}",
                sanitize(reason),
                sanitize(method),
                sanitize(path),
                remoteAddr == null ? "unknown" : sanitize(remoteAddr),
                keyId == null ? "none" : sanitize(keyId));
    }

    /**
     * Records a successful authentication by incrementing the counter for {@code (application, method, path)}.
     * Excess distinct keys (beyond {@code maxCounterKeys}) are folded into a single overflow bucket.
     */
    public void success(String applicationUniqueId, String method, String path) {
        var key = new AuditKey(sanitize(applicationUniqueId), sanitize(method), sanitize(path));
        var adder = successCounters.get(key);
        if (adder == null) {
            if (successCounters.size() >= maxCounterKeys) {
                key = OVERFLOW_KEY;
                if (overflowWarned.compareAndSet(false, true)) {
                    AUDIT.warn("event=api.auth.audit outcome=OVERFLOW maxCounterKeys={} note=further distinct success keys folded into __overflow__",
                            maxCounterKeys);
                }
            }
            adder = successCounters.computeIfAbsent(key, k -> new LongAdder());
        }
        adder.increment();
    }

    /**
     * Flushes the aggregated success counters to the audit log and resets the window.
     */
    @Scheduled(fixedDelayString = "${identity.rest.api.audit.flush-interval:PT10M}")
    void flush() {
        var windowEnd = clock.instant();
        var start = windowStart.getAndSet(windowEnd);
        successCounters.forEach((key, adder) -> {
            var count = adder.sumThenReset();
            if (count > 0) {
                AUDIT.info("event=api.auth outcome=SUCCESS applicationUniqueId={} method={} path={} count={} windowStart={} windowEnd={}",
                        key.applicationUniqueId(), key.method(), key.path(), count, start, windowEnd);
            }
        });
    }

    @PreDestroy
    void flushOnShutdown() {
        flush();
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "none";
        }
        var cleaned = value.replaceAll("[\\p{Cntrl}\\s]", "_");
        return cleaned.length() > MAX_FIELD_LENGTH ? cleaned.substring(0, MAX_FIELD_LENGTH) : cleaned;
    }

    private record AuditKey(String applicationUniqueId, String method, String path) {
    }
}
