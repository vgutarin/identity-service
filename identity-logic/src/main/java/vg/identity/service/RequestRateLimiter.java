package vg.identity.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory, per-key fixed-window rate limiter used to bound abusive request rates on public, unauthenticated
 * surfaces — notably the password-recovery request endpoint (FR-007a), keyed by client IP.
 * <p>
 * The state is per-process: at the current single-instance deployment scale this is sufficient, and the
 * per-email cooldown (which is DB-backed) remains correct across instances. If the app is ever scaled
 * horizontally the effective limit multiplies by the instance count; revisit with a shared store then.
 * <p>
 * Stale per-key entries are pruned lazily: a re-hit key resets itself on {@link #tryAcquire}, and a bounded
 * sweep (at most once per window) drops entries for keys that were never hit again, so the map cannot grow
 * without bound from one-off client keys.
 * <p>
 * Intentionally not a Spring component — it is constructed as a bean in {@code IdentityLogicConfig} with its
 * limit/window and the shared {@link Clock}, which keeps it trivially unit-testable with a controllable clock.
 */
public class RequestRateLimiter {

    private final int maxRequests;
    private final Duration window;
    private final Clock clock;
    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();
    private final AtomicReference<Instant> nextCleanup;

    public RequestRateLimiter(int maxRequests, Duration window, Clock clock) {
        this.maxRequests = maxRequests;
        this.window = window;
        this.clock = clock;
        this.nextCleanup = new AtomicReference<>(clock.instant().plus(window));
    }

    /**
     * Records one request for {@code key} and reports whether it is within the allowed rate.
     *
     * @return {@code true} if the request is allowed, {@code false} if {@code key} has exceeded
     * {@code maxRequests} within the current {@code window}. A blank key is always allowed (the caller could
     * not resolve a client identifier, so there is nothing to throttle on).
     */
    public boolean tryAcquire(String key) {
        if (key == null || key.isBlank()) {
            return true;
        }
        var now = clock.instant();
        var counter = counters.compute(key, (k, existing) -> {
            if (existing == null || !existing.withinWindow(now, window)) {
                return new Counter(now, 1);
            }
            return existing.increment();
        });
        cleanUpIfDue(now);
        return counter.count() <= maxRequests;
    }

    /**
     * At most once per {@code window}, drop entries whose window has fully elapsed. A single thread wins the
     * {@link AtomicReference#compareAndSet} and runs the sweep; the rest skip it. Conditional
     * {@link ConcurrentHashMap#remove(Object, Object)} removes an entry only if it has not been refreshed
     * concurrently, so a key hit again mid-sweep keeps its live counter.
     */
    private void cleanUpIfDue(Instant now) {
        var due = nextCleanup.get();
        if (now.isBefore(due)) {
            return;
        }
        if (!nextCleanup.compareAndSet(due, now.plus(window))) {
            return;
        }
        counters.forEach((key, counter) -> {
            if (!counter.withinWindow(now, window)) {
                counters.remove(key, counter);
            }
        });
    }

    /** Number of client keys currently tracked. Package-private for tests to assert lazy eviction. */
    int trackedKeyCount() {
        return counters.size();
    }

    private record Counter(Instant windowStart, int count) {
        boolean withinWindow(Instant now, Duration window) {
            return now.isBefore(windowStart.plus(window));
        }

        Counter increment() {
            return new Counter(windowStart, count + 1);
        }
    }
}
