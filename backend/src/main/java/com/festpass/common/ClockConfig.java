package com.festpass.common;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * All "now" values come from this Clock so tests can control time.
 * It ticks in whole microseconds, the precision of PostgreSQL timestamps, so a time saved
 * and read back is exactly the time that was compared in Java.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
    }
}
