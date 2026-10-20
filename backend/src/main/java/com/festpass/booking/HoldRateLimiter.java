package com.festpass.booking;

import com.festpass.common.RateLimitedException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Per-user limit on hold requests (PLAN.md 5.7): a few per second and a few dozen per minute.
 * Buckets live in memory, which is fine for a single app instance. Time comes from the
 * injected Clock, so tests control refills.
 */
@Component
public class HoldRateLimiter {

    private final Map<Long, Bucket> buckets = new ConcurrentHashMap<>();
    private final int perSecond;
    private final int perMinute;
    private final TimeMeter timeMeter;

    public HoldRateLimiter(@Value("${festpass.rate-limit.holds-per-second}") int perSecond,
                           @Value("${festpass.rate-limit.holds-per-minute}") int perMinute,
                           Clock clock) {
        this.perSecond = perSecond;
        this.perMinute = perMinute;
        this.timeMeter = new ClockTimeMeter(clock);
    }

    /** Uses up one request for the user, or throws 429 RATE_LIMITED with a Retry-After value. */
    public void check(Long userId) {
        ConsumptionProbe probe = buckets.computeIfAbsent(userId, id -> newBucket()).tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            long retryAfterSeconds = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds() + 1);
            throw new RateLimitedException(retryAfterSeconds);
        }
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(perSecond).refillIntervally(perSecond, Duration.ofSeconds(1)).build())
                .addLimit(Bandwidth.builder().capacity(perMinute).refillIntervally(perMinute, Duration.ofMinutes(1)).build())
                .withCustomTimePrecision(timeMeter)
                .build();
    }

    private record ClockTimeMeter(Clock clock) implements TimeMeter {
        @Override
        public long currentTimeNanos() {
            var now = clock.instant();
            return now.getEpochSecond() * 1_000_000_000L + now.getNano();
        }

        @Override
        public boolean isWallClockBased() {
            return true;
        }
    }
}
