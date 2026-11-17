package com.festpass.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.festpass.TestcontainersConfig;
import com.festpass.event.Event;
import com.festpass.event.EventRepository;
import com.festpass.user.Role;
import com.festpass.user.UserRepository;
import com.festpass.user.UserService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** The demo profile wipes everything on start and recreates the sample data with a 2-minute flash drop. */
@SpringBootTest
@ActiveProfiles("demo")
@Import(TestcontainersConfig.class)
class DemoDataSeederTest {

    @Autowired UserRepository users;
    @Autowired UserService userService;
    @Autowired EventRepository events;
    @Autowired DemoDataSeeder seeder;

    @Test
    void everyStartResetsToTheSampleData() throws Exception {
        userService.create("Leftover", "leftover@example.com", "password123", Role.STUDENT);
        assertThat(users.existsByEmail("leftover@example.com")).isTrue();

        seeder.run(new DefaultApplicationArguments());

        assertThat(users.existsByEmail("leftover@example.com")).isFalse();
        assertThat(users.count()).isEqualTo(Role.values().length);
        assertThat(events.count()).isEqualTo(4);
        Event drop = events.findBySlug(SampleData.FLASH_DROP_SLUG).orElseThrow();
        assertThat(drop.getSaleOpensAt())
                .isCloseTo(Instant.now().plus(DemoDataSeeder.FLASH_DROP_DELAY), within(30, ChronoUnit.SECONDS));
    }
}
