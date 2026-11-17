package com.festpass.seed;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Sample data for local development ({@code dev} profile). Safe on every start: it only adds what is
 * missing, keeps the sample events in the future, and opens the flash drop's sale 3 minutes after startup.
 * Your own test data is kept between restarts.
 */
@Component
@Profile("dev")
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    static final Duration FLASH_DROP_DELAY = Duration.ofMinutes(3);

    private final SampleData sampleData;

    public DevDataSeeder(SampleData sampleData) {
        this.sampleData = sampleData;
    }

    @Override
    public void run(ApplicationArguments args) {
        sampleData.ensure(FLASH_DROP_DELAY);
        sampleData.keepCurrent(FLASH_DROP_DELAY);
        log.info("Dev data ready. Log in as student@, organizer@, gate@ or admin@festpass.local with password '{}'",
                SampleData.PASSWORD);
    }
}
