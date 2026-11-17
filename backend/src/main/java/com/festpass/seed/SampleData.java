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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The sample accounts and events used by the {@code dev} and {@code demo} profiles.
 * All sample accounts use the password {@value #PASSWORD}: never enable those profiles in production.
 */
@Component
public class SampleData {

    private static final Logger log = LoggerFactory.getLogger(SampleData.class);

    public static final String PASSWORD = "festpass123";
    public static final String SHOW_SLUG = "rhythm-night-saturday-showcase";
    public static final String OPEN_AIR_SLUG = "open-air-sunset-sessions";
    public static final String FLASH_DROP_SLUG = "midnight-drop-dj-night";
    public static final String STUDIO_SLUG = "studio-session-unplugged";

    private final UserService userService;
    private final UserRepository users;
    private final EventService eventService;
    private final EventLayoutService layoutService;
    private final EventRepository events;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;

    public SampleData(UserService userService, UserRepository users, EventService eventService,
                      EventLayoutService layoutService, EventRepository events, JdbcTemplate jdbc,
                      TransactionTemplate tx, Clock clock) {
        this.userService = userService;
        this.users = users;
        this.eventService = eventService;
        this.layoutService = layoutService;
        this.events = events;
        this.jdbc = jdbc;
        this.tx = tx;
        this.clock = clock;
    }

    /** One sample event: how far ahead it starts, and how to build it. */
    private record Sample(Duration startsIn, BiConsumer<Instant, AppUserPrincipal> build) {}

    /** Creates whatever sample accounts and events are missing. Existing ones are left alone. */
    public void ensure(Duration flashDropOpensIn) {
        tx.executeWithoutResult(status -> {
            account("Aarav Student", "student@festpass.local", Role.STUDENT);
            User organizer = account("Riya Organizer", "organizer@festpass.local", Role.ORGANIZER);
            account("Kabir Gate", "gate@festpass.local", Role.GATE_STAFF);
            account("Meera Admin", "admin@festpass.local", Role.ADMIN);
            AppUserPrincipal org = new AppUserPrincipal(organizer);
            Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MINUTES);

            samples(flashDropOpensIn).forEach((slug, sample) -> {
                if (events.findBySlug(slug).isEmpty()) {
                    sample.build().accept(now.plus(sample.startsIn()), org);
                    log.info("Created sample event {}", slug);
                }
            });
        });
        // The gate account can scan at every sample event.
        jdbc.update("""
                INSERT INTO gate_assignments (user_id, event_id)
                SELECT u.id, e.id FROM users u, events e
                WHERE u.email = 'gate@festpass.local' AND e.slug IN (?, ?, ?, ?)
                ON CONFLICT DO NOTHING""", SHOW_SLUG, OPEN_AIR_SLUG, FLASH_DROP_SLUG, STUDIO_SLUG);
    }

    /**
     * Moves sample events that have started (or start within a day) back into the future, keeping their
     * length, with sales open now; the flash drop's sale opens {@code flashDropOpensIn} from now.
     */
    public void keepCurrent(Duration flashDropOpensIn) {
        tx.executeWithoutResult(status -> {
            Instant now = Instant.now(clock);
            samples(flashDropOpensIn).forEach((slug, sample) -> events.findBySlug(slug).ifPresent(event -> {
                if (event.getStartsAt().isBefore(now.plus(Duration.ofDays(1)))) {
                    Duration length = Duration.between(event.getStartsAt(), event.getEndsAt());
                    Instant start = now.plus(sample.startsIn()).truncatedTo(ChronoUnit.MINUTES);
                    event.setStartsAt(start);
                    event.setEndsAt(start.plus(length));
                    event.setSaleClosesAt(start);
                    event.setSaleOpensAt(now.minus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MINUTES));
                    log.info("Moved sample event '{}' to {}", event.getTitle(), start);
                }
                if (slug.equals(FLASH_DROP_SLUG)) {
                    Instant opensAt = now.plus(flashDropOpensIn).truncatedTo(ChronoUnit.SECONDS);
                    event.setSaleOpensAt(opensAt);
                    log.info("Flash drop '{}' opens at {}", event.getTitle(), opensAt);
                }
            }));
        });
    }

    /** Deletes every row of every table (not the schema), for the demo profile's clean start. */
    public void wipeEverything() {
        List<String> tables = jdbc.queryForList("""
                SELECT tablename FROM pg_tables
                WHERE schemaname = current_schema() AND tablename <> 'flyway_schema_history'""", String.class);
        if (!tables.isEmpty()) {
            jdbc.execute("TRUNCATE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE");
        }
    }

    private User account(String name, String email, Role role) {
        return users.findByEmail(email).orElseGet(() -> userService.create(name, email, PASSWORD, role));
    }

    private Map<String, Sample> samples(Duration flashDropOpensIn) {
        Map<String, Sample> samples = new LinkedHashMap<>();

        // A seated auditorium show: 202 seats in 3 tiers.
        samples.put(SHOW_SLUG, new Sample(Duration.ofDays(7), (start, org) -> {
            EventDetail show = create(new EventRequest("Rhythm Night: Saturday Showcase",
                    "The dance society's biggest night: six crews, one stage, and a surprise finale.",
                    EventCategory.DANCE, null, "Main Auditorium, TIET", start, start.plus(Duration.ofHours(3)),
                    salesOpen(), start, SeatingMode.SEATED, 5, 4, false), org);
            TierView vip = tier(show, "VIP", 79_900, null, "#ff3d81", org);
            TierView gold = tier(show, "Gold", 49_900, null, "#7c5cff", org);
            TierView silver = tier(show, "Silver", 24_900, null, "#22d3ee", org);
            section(show, "Front", vip, 2, 16, List.of(4, 12), org);
            section(show, "Middle", gold, 5, 18, List.of(4, 14), org);
            section(show, "Back", silver, 4, 20, List.of(5, 15), org);
            eventService.publish(show.id(), org);
        }));

        // General admission on the lawns: a 100-ticket Fan Pit (the Stress Arena's GA_RUSH target) and 500 general.
        samples.put(OPEN_AIR_SLUG, new Sample(Duration.ofDays(14), (start, org) -> {
            EventDetail openAir = create(new EventRequest("Open Air: Sunset Sessions",
                    "Indie bands and acoustic sets on the lawns as the sun goes down. Bring a friend and a blanket.",
                    EventCategory.MUSIC, null, "Central Lawns, TIET", start, start.plus(Duration.ofHours(4)),
                    salesOpen(), start, SeatingMode.GENERAL, 5, 4, false), org);
            tier(openAir, "Fan Pit", 59_900, 100, "#f59e0b", org);
            tier(openAir, "General", 29_900, 500, "#22e39a", org);
            eventService.publish(openAir.id(), org);
        }));

        // A seated flash drop with a waiting room.
        samples.put(FLASH_DROP_SLUG, new Sample(Duration.ofDays(3), (start, org) -> {
            Instant opensAt = Instant.now(clock).plus(flashDropOpensIn).truncatedTo(ChronoUnit.SECONDS);
            EventDetail drop = create(new EventRequest("Midnight Drop: DJ Night",
                    "Limited seats, one drop. Join the waiting room before the countdown hits zero.",
                    EventCategory.MUSIC, null, "Open Air Theatre, TIET", start, start.plus(Duration.ofHours(4)),
                    opensAt, start, SeatingMode.SEATED, 5, 2, true), org);
            TierView floor = tier(drop, "Floor", 39_900, null, "#22e39a", org);
            TierView balcony = tier(drop, "Balcony", 19_900, null, "#7c5cff", org);
            section(drop, "Floor", floor, 6, 20, List.of(10), org);
            section(drop, "Balcony", balcony, 3, 24, List.of(6, 18), org);
            eventService.publish(drop.id(), org);
        }));

        // A small 50-seat room: the Stress Arena's "500 bots against 50 seats" target.
        samples.put(STUDIO_SLUG, new Sample(Duration.ofDays(10), (start, org) -> {
            EventDetail studio = create(new EventRequest("Studio Session: Unplugged",
                    "An intimate acoustic night in the music room. Just fifty seats.",
                    EventCategory.MUSIC, null, "Music Room, Student Activity Centre", start,
                    start.plus(Duration.ofHours(2)), salesOpen(), start, SeatingMode.SEATED, 5, 4, false), org);
            TierView standard = tier(studio, "Standard", 19_900, null, "#7c5cff", org);
            section(studio, "Room", standard, 5, 10, List.of(5), org);
            eventService.publish(studio.id(), org);
        }));
        return samples;
    }

    private Instant salesOpen() {
        return Instant.now(clock).minus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MINUTES);
    }

    private EventDetail create(EventRequest request, AppUserPrincipal org) {
        return eventService.create(request, org);
    }

    private TierView tier(EventDetail event, String name, long pricePaise, Integer capacity, String color,
                          AppUserPrincipal org) {
        return layoutService.addTier(event.id(), new TierRequest(name, pricePaise, capacity, color), org);
    }

    private void section(EventDetail event, String name, TierView tier, int rows, int seatsPerRow,
                         List<Integer> aisleAfter, AppUserPrincipal org) {
        layoutService.addSection(event.id(), new SectionRequest(name, tier.id(), rows, seatsPerRow, aisleAfter), org);
    }
}
