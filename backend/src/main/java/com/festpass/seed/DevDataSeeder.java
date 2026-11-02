package com.festpass.seed;

import com.festpass.auth.AppUserPrincipal;
import com.festpass.event.EventDtos.EventDetail;
import com.festpass.event.EventDtos.EventRequest;
import com.festpass.event.EventDtos.SectionRequest;
import com.festpass.event.EventDtos.TierRequest;
import com.festpass.event.EventDtos.TierView;
import com.festpass.event.EventEnums.EventCategory;
import com.festpass.event.EventEnums.SeatingMode;
import com.festpass.event.EventLayoutService;
import com.festpass.event.EventRepository;
import com.festpass.event.EventService;
import com.festpass.user.Role;
import com.festpass.user.User;
import com.festpass.user.UserRepository;
import com.festpass.user.UserService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sample data for local development ({@code dev} profile): one user per role and three
 * published events. Safe to run on every start: it only creates data once, but it keeps the
 * sample events in the future and moves the flash drop's sale opening to 3 minutes after startup,
 * so the data works however long ago it was created.
 *
 * <p>All dev accounts use the password {@value #PASSWORD}. Never enable this profile in production.
 */
@Component
@Profile("dev")
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    static final String PASSWORD = "festpass123";
    static final String FLASH_DROP_SLUG = "midnight-drop-dj-night";
    static final Duration FLASH_DROP_DELAY = Duration.ofMinutes(3);

    private final UserService userService;
    private final UserRepository users;
    private final EventService eventService;
    private final EventLayoutService layoutService;
    private final EventRepository events;
    private final TransactionTemplate tx;
    private final Clock clock;

    public DevDataSeeder(UserService userService, UserRepository users, EventService eventService,
                         EventLayoutService layoutService, EventRepository events, TransactionTemplate tx, Clock clock) {
        this.userService = userService;
        this.users = users;
        this.eventService = eventService;
        this.layoutService = layoutService;
        this.events = events;
        this.tx = tx;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!users.existsByEmail("admin@festpass.local")) {
            tx.executeWithoutResult(status -> seed());
            log.info("Dev data created. Log in as student@, organizer@, gate@ or admin@festpass.local with password '{}'",
                    PASSWORD);
        }
        keepSampleEventsCurrent();
    }

    private void seed() {
        userService.create("Aarav Student", "student@festpass.local", PASSWORD, Role.STUDENT);
        User organizer = userService.create("Riya Organizer", "organizer@festpass.local", PASSWORD, Role.ORGANIZER);
        userService.create("Kabir Gate", "gate@festpass.local", PASSWORD, Role.GATE_STAFF);
        userService.create("Meera Admin", "admin@festpass.local", PASSWORD, Role.ADMIN);
        AppUserPrincipal org = new AppUserPrincipal(organizer);

        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MINUTES);

        // 1. Seated auditorium show: 202 seats in 3 tiers.
        Instant showStart = now.plus(Duration.ofDays(7));
        EventDetail show = eventService.create(new EventRequest(
                "Rhythm Night: Saturday Showcase",
                "The dance society's biggest night: six crews, one stage, and a surprise finale.",
                EventCategory.DANCE, null, "Main Auditorium, TIET",
                showStart, showStart.plus(Duration.ofHours(3)), now.minus(Duration.ofDays(1)), showStart,
                SeatingMode.SEATED, 5, 4, false), org);
        TierView vip = tier(show, "VIP", 79_900, null, "#ff3d81", org);
        TierView gold = tier(show, "Gold", 49_900, null, "#7c5cff", org);
        TierView silver = tier(show, "Silver", 24_900, null, "#22d3ee", org);
        section(show, "Front", vip, 2, 16, List.of(4, 12), org);
        section(show, "Middle", gold, 5, 18, List.of(4, 14), org);
        section(show, "Back", silver, 4, 20, List.of(5, 15), org);
        eventService.publish(show.id(), org);

        // 2. General-admission open-air event: 2 tiers.
        Instant gaStart = now.plus(Duration.ofDays(14));
        EventDetail openAir = eventService.create(new EventRequest(
                "Open Air: Sunset Sessions",
                "Indie bands and acoustic sets on the lawns as the sun goes down. Bring a friend and a blanket.",
                EventCategory.MUSIC, null, "Central Lawns, TIET",
                gaStart, gaStart.plus(Duration.ofHours(4)), now.minus(Duration.ofDays(1)), gaStart,
                SeatingMode.GENERAL, 5, 4, false), org);
        tier(openAir, "Fan Pit", 59_900, 100, "#f59e0b", org);
        tier(openAir, "General", 29_900, 500, "#22e39a", org);
        eventService.publish(openAir.id(), org);

        // 3. Flash drop: seated, sale opens 3 minutes after startup (see rescheduleFlashDrop).
        Instant dropStart = now.plus(Duration.ofDays(3));
        EventDetail drop = eventService.create(new EventRequest(
                "Midnight Drop: DJ Night",
                "Limited seats, one drop. Join the waiting room before the countdown hits zero.",
                EventCategory.MUSIC, null, "Open Air Theatre, TIET",
                dropStart, dropStart.plus(Duration.ofHours(4)), now.plus(FLASH_DROP_DELAY), dropStart,
                SeatingMode.SEATED, 5, 2, true), org);
        TierView floor = tier(drop, "Floor", 39_900, null, "#22e39a", org);
        TierView balcony = tier(drop, "Balcony", 19_900, null, "#7c5cff", org);
        section(drop, "Floor", floor, 6, 20, List.of(10), org);
        section(drop, "Balcony", balcony, 3, 24, List.of(6, 18), org);
        eventService.publish(drop.id(), org);
    }

    private TierView tier(EventDetail event, String name, long pricePaise, Integer capacity, String color,
                          AppUserPrincipal org) {
        return layoutService.addTier(event.id(), new TierRequest(name, pricePaise, capacity, color), org);
    }

    private void section(EventDetail event, String name, TierView tier, int rows, int seatsPerRow,
                         List<Integer> aisleAfter, AppUserPrincipal org) {
        layoutService.addSection(event.id(), new SectionRequest(name, tier.id(), rows, seatsPerRow, aisleAfter), org);
    }

    /** How far ahead each sample event should start, so the dev data never goes stale. */
    private static final Map<String, Duration> SAMPLE_STARTS_IN = Map.of(
            "rhythm-night-saturday-showcase", Duration.ofDays(7),
            "open-air-sunset-sessions", Duration.ofDays(14),
            FLASH_DROP_SLUG, Duration.ofDays(3));

    /**
     * Moves sample events that have started (or start within a day) back into the future, keeping
     * their length, with sales open now. The flash drop's sale always opens 3 minutes after startup.
     */
    private void keepSampleEventsCurrent() {
        tx.executeWithoutResult(status -> {
            Instant now = Instant.now(clock);
            SAMPLE_STARTS_IN.forEach((slug, startsIn) -> events.findBySlug(slug).ifPresent(event -> {
                if (event.getStartsAt().isBefore(now.plus(Duration.ofDays(1)))) {
                    Duration length = Duration.between(event.getStartsAt(), event.getEndsAt());
                    Instant start = now.plus(startsIn).truncatedTo(ChronoUnit.MINUTES);
                    event.setStartsAt(start);
                    event.setEndsAt(start.plus(length));
                    event.setSaleClosesAt(start);
                    event.setSaleOpensAt(now.minus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MINUTES));
                    log.info("Moved sample event '{}' to {}", event.getTitle(), start);
                }
                if (slug.equals(FLASH_DROP_SLUG)) {
                    Instant opensAt = now.plus(FLASH_DROP_DELAY).truncatedTo(ChronoUnit.SECONDS);
                    event.setSaleOpensAt(opensAt);
                    log.info("Flash drop '{}' opens at {}", event.getTitle(), opensAt);
                }
            }));
        });
    }
}
