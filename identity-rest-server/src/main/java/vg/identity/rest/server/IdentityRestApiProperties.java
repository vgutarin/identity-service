package vg.identity.rest.server;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration for the optional REST API surface.
 *
 * <p>Bound unconditionally (see {@link IdentityRestApiAutoConfig}) so that an unparseable value for
 * {@code identity.rest.api.enabled} fails application startup instead of silently disabling the API.</p>
 */
@ConfigurationProperties(prefix = "identity.rest.api")
public class IdentityRestApiProperties {

    /**
     * Whether the REST API surface is exposed. Safe default: disabled.
     */
    private boolean enabled = false;

    private final Audit audit = new Audit();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Audit getAudit() {
        return audit;
    }

    /**
     * Security-audit logging for the REST API. Authentication failures are logged per-event; successes are
     * counted and flushed periodically.
     */
    public static class Audit {

        /**
         * How often the aggregated success counters are flushed to the audit log.
         */
        private Duration flushInterval = Duration.ofMinutes(10);

        /**
         * Upper bound on the number of distinct success-counter keys held in memory. Beyond this, further
         * distinct keys are folded into a single {@code other} bucket to cap memory use.
         */
        private int maxCounterKeys = 10_000;

        public Duration getFlushInterval() {
            return flushInterval;
        }

        public void setFlushInterval(Duration flushInterval) {
            this.flushInterval = flushInterval;
        }

        public int getMaxCounterKeys() {
            return maxCounterKeys;
        }

        public void setMaxCounterKeys(int maxCounterKeys) {
            this.maxCounterKeys = maxCounterKeys;
        }
    }
}
