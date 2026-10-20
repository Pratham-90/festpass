package com.festpass;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * A clock that stands still until a test moves it. Every integration test starts with the
 * clock frozen at the real current time; tests move it to make holds expire, instead of sleeping.
 * It ticks in whole microseconds, like PostgreSQL timestamps, so a time saved and read back
 * compares equal to the clock (PostgreSQL rounds nanoseconds, which could land just after it).
 */
public class MutableClock extends Clock {

    private volatile Instant now = realNow();

    public void reset() {
        now = realNow();
    }

    private static Instant realNow() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
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
        throw new UnsupportedOperationException("FestPass always works in UTC");
    }
}
