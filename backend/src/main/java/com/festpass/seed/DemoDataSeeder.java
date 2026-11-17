package com.festpass.seed;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * The {@code demo} profile, for presenting FestPass (docs/demo-script.md): every start wipes all data
 * and recreates the sample accounts and events, with the flash drop opening 2 minutes after startup.
 * Never point this at a database whose data you want to keep.
 */
@Component
@Profile("demo")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    static final Duration FLASH_DROP_DELAY = Duration.ofMinutes(2);

    private final SampleData sampleData;

    public DemoDataSeeder(SampleData sampleData) {
        this.sampleData = sampleData;
    }

    @Override
    public void run(ApplicationArguments args) {
        sampleData.wipeEverything();
        sampleData.ensure(FLASH_DROP_DELAY);
        log.warn("DEMO MODE: all data was reset. The flash drop opens in 2 minutes. Accounts use password '{}'",
                SampleData.PASSWORD);
    }
}
