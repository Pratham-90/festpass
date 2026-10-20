package com.festpass.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.festpass.TestcontainersConfig;
import com.festpass.event.Event;
import com.festpass.event.EventEnums.EventStatus;
import com.festpass.event.EventEnums.SeatingMode;
import com.festpass.event.EventRepository;
import com.festpass.event.TicketTierRepository;
import com.festpass.seating.SeatRepository;
import com.festpass.user.Role;
import com.festpass.user.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Runs with the dev profile (its own context and database) to check the seed data. */
@SpringBootTest
@ActiveProfiles("dev")
@Import(TestcontainersConfig.class)
class DevDataSeederTest {

    @Autowired UserRepository users;
    @Autowired EventRepository events;
    @Autowired TicketTierRepository tiers;
    @Autowired SeatRepository seats;
    @Autowired DevDataSeeder seeder;

    @Test
    void seedsOneUserPerRoleAndThreePublishedEvents() {
        for (Role role : Role.values()) {
            assertThat(users.findAll()).anyMatch(u -> u.getRole() == role);
        }

        List<Event> all = events.findAll();
        assertThat(all).hasSize(3).allMatch(e -> e.getStatus() == EventStatus.PUBLISHED);

        Event show = events.findBySlug("rhythm-night-saturday-showcase").orElseThrow();
        assertThat(show.getSeatingMode()).isEqualTo(SeatingMode.SEATED);
        assertThat(tiers.countByEventId(show.getId())).isEqualTo(3);
        assertThat(seats.countByEventId(show.getId())).isBetween(190L, 210L);

        Event openAir = events.findBySlug("open-air-sunset-sessions").orElseThrow();
        assertThat(openAir.getSeatingMode()).isEqualTo(SeatingMode.GENERAL);
        assertThat(tiers.countByEventId(openAir.getId())).isEqualTo(2);
        assertThat(seats.countByEventId(openAir.getId())).isZero();

        Event drop = events.findBySlug(DevDataSeeder.FLASH_DROP_SLUG).orElseThrow();
        assertThat(drop.isFlashDrop()).isTrue();
        assertThat(drop.getSaleOpensAt())
                .isCloseTo(Instant.now().plus(DevDataSeeder.FLASH_DROP_DELAY), within(30, ChronoUnit.SECONDS));
    }

    @Test
    void runningAgainDoesNotDuplicateData() throws Exception {
        long usersBefore = users.count();
        ApplicationArguments args = new DefaultApplicationArguments();
        seeder.run(args);
        assertThat(users.count()).isEqualTo(usersBefore);
        assertThat(events.count()).isEqualTo(3);
    }
}
