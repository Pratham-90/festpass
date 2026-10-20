package com.festpass.common;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on @Scheduled jobs (hold expiry) and @Async methods (emails).
 * Integration tests set festpass.scheduling.enabled=false and run the jobs themselves,
 * so a background job never changes data in the middle of a test.
 */
@Configuration
@EnableAsync
public class SchedulingConfig {

    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "festpass.scheduling.enabled", havingValue = "true", matchIfMissing = true)
    static class ScheduledJobs {}
}
