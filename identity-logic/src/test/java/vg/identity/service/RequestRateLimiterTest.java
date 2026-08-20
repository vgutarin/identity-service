package vg.identity.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class RequestRateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-08-20T00:00:00Z"));

    @Test
    void tryAcquire_allowsUpToMaxWithinWindowThenDenies() {
        var limiter = new RequestRateLimiter(3, Duration.ofMinutes(10), clock);

        assertThat(limiter.tryAcquire("1.2.3.4")).isTrue();
        assertThat(limiter.tryAcquire("1.2.3.4")).isTrue();
        assertThat(limiter.tryAcquire("1.2.3.4")).isTrue();
        assertThat(limiter.tryAcquire("1.2.3.4")).isFalse();
        assertThat(limiter.tryAcquire("1.2.3.4")).isFalse();
    }

    @Test
    void tryAcquire_tracksKeysIndependently() {
        var limiter = new RequestRateLimiter(1, Duration.ofMinutes(10), clock);

        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isFalse();
        // A different client key has its own budget.
        assertThat(limiter.tryAcquire("b")).isTrue();
    }

    @Test
    void tryAcquire_resetsAfterWindowElapses() {
        var limiter = new RequestRateLimiter(1, Duration.ofMinutes(10), clock);

        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isFalse();

        clock.advance(Duration.ofMinutes(10));
        // New window: the budget is available again.
        assertThat(limiter.tryAcquire("a")).isTrue();
    }

    @Test
    void tryAcquire_lazilyEvictsEntriesForKeysNotHitAgain() {
        var limiter = new RequestRateLimiter(5, Duration.ofMinutes(10), clock);

        limiter.tryAcquire("a");
        limiter.tryAcquire("b");
        assertThat(limiter.trackedKeyCount()).isEqualTo(2);

        // Past the window, a fresh call triggers the once-per-window sweep; "a"/"b" are stale and dropped,
        // while the key that triggered the sweep remains.
        clock.advance(Duration.ofMinutes(10));
        limiter.tryAcquire("c");

        assertThat(limiter.trackedKeyCount()).isEqualTo(1);
    }

    @Test
    void tryAcquire_alwaysAllowsBlankKey() {
        var limiter = new RequestRateLimiter(1, Duration.ofMinutes(10), clock);

        assertThat(limiter.tryAcquire(null)).isTrue();
        assertThat(limiter.tryAcquire("")).isTrue();
        assertThat(limiter.tryAcquire("   ")).isTrue();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
