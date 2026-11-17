package com.festpass.arena;

import com.festpass.event.Event;
import com.festpass.event.EventEnums.EventStatus;
import com.festpass.event.EventEnums.SeatingMode;
import com.festpass.event.EventRepository;
import com.festpass.event.TicketTier;
import com.festpass.event.TicketTierRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Stress Arena's data: a sandbox copy of a real event, the bot accounts that fight over it,
 * the integrity checks run afterwards, and "Reset arena". Real events are only ever read here.
 */
@Component
public class ArenaSandbox {

    /** A bot's account. Bots log in with a password the arena keeps in memory for the run. */
    public record Bot(int index, long userId, String email) {}

    /** The checks that prove nothing was oversold. Every number must be zero. */
    public record Integrity(long seatsWithSeveralTickets, long tiersReservedOverCapacity,
                            long tiersWithMoreTicketsThanCapacity, long paidOrdersWithoutTickets,
                            long bookedSeatsWithoutTicket) {
        public boolean allZero() {
            return seatsWithSeveralTickets + tiersReservedOverCapacity + tiersWithMoreTicketsThanCapacity
                    + paidOrdersWithoutTickets + bookedSeatsWithoutTicket == 0;
        }
    }

    /** Where the sandbox ended up: capacity, tickets sold, and tickets still sitting in holds. */
    public record Totals(long capacity, long ticketsSold, long ticketsHeld) {}

    private final JdbcTemplate jdbc;
    private final EventRepository events;
    private final TicketTierRepository tiers;
    private final Clock clock;

    public ArenaSandbox(JdbcTemplate jdbc, EventRepository events, TicketTierRepository tiers, Clock clock) {
        this.jdbc = jdbc;
        this.events = events;
        this.tiers = tiers;
        this.clock = clock;
    }

    /**
     * Copies an event, its tiers and its seat layout into a hidden sandbox event that is on sale right now.
     * Nothing about the source event changes.
     */
    @Transactional
    public Event cloneEvent(Event source, long runId, Long adminId) {
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS);
        Event copy = new Event(adminId, source.getSlug() + "-arena-" + runId, source.getSeatingMode(), now);
        copy.setTitle("[Arena #" + runId + "] " + source.getTitle());
        copy.setDescription("Stress Arena sandbox copy of " + source.getTitle());
        copy.setCategory(source.getCategory());
        copy.setBannerUrl(source.getBannerUrl());
        copy.setVenueName(source.getVenueName());
        copy.setStartsAt(now.plus(Duration.ofDays(1)));
        copy.setEndsAt(now.plus(Duration.ofDays(1)).plus(Duration.between(source.getStartsAt(), source.getEndsAt())));
        copy.setSaleOpensAt(now.minus(Duration.ofMinutes(1)));
        copy.setSaleClosesAt(now.plus(Duration.ofDays(1)));
        copy.setHoldMinutes(source.getHoldMinutes());
        copy.setMaxTicketsPerUser(source.getMaxTicketsPerUser());
        copy.setFlashDrop(source.isFlashDrop());
        copy.setSandbox(true);
        copy.setStatus(EventStatus.PUBLISHED);
        events.saveAndFlush(copy);

        Map<Long, Long> tierCopies = new LinkedHashMap<>();
        for (TicketTier tier : tiers.findByEventIdOrderByIdAsc(source.getId())) {
            TicketTier tierCopy = tiers.save(new TicketTier(copy.getId(), tier.getName(), tier.getPricePaise(),
                    tier.getCapacity(), tier.getColorHex()));
            tierCopies.put(tier.getId(), tierCopy.getId());
        }
        tiers.flush();

        if (source.getSeatingMode() == SeatingMode.SEATED && !tierCopies.isEmpty()) {
            // One statement copies every seat, mapping each to its tier's copy.
            String mapping = tierCopies.entrySet().stream()
                    .map(e -> "(" + e.getKey() + "," + e.getValue() + ")")
                    .collect(Collectors.joining(","));
            jdbc.update("""
                    INSERT INTO seats (event_id, tier_id, section, row_label, seat_number, x, y, status)
                    SELECT ?, m.new_id, s.section, s.row_label, s.seat_number, s.x, s.y, 'AVAILABLE'
                    FROM seats s JOIN (VALUES %s) AS m(old_id, new_id) ON m.old_id = s.tier_id
                    WHERE s.event_id = ?""".formatted(mapping), copy.getId(), source.getId());
        }
        return copy;
    }

    /** Creates the bot accounts in one batch. They share one (deliberately cheap) password hash. */
    @Transactional
    public List<Bot> createBots(long runId, int count, String passwordHash) {
        Timestamp now = Timestamp.from(Instant.now(clock));
        List<Object[]> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(new Object[]{"Bot %04d".formatted(i + 1), email(runId, i), passwordHash, now});
        }
        jdbc.batchUpdate("""
                INSERT INTO users (name, email, password_hash, role, created_at, is_bot)
                VALUES (?, ?, ?, 'STUDENT', ?, TRUE)""", rows);
        List<Long> ids = jdbc.queryForList(
                "SELECT id FROM users WHERE email LIKE ? ORDER BY id", Long.class, "bot-" + runId + "-%@arena.festpass.local");
        List<Bot> bots = new ArrayList<>(count);
        for (int i = 0; i < ids.size(); i++) {
            bots.add(new Bot(i, ids.get(i), email(runId, i)));
        }
        return bots;
    }

    private static String email(long runId, int index) {
        return "bot-" + runId + "-" + index + "@arena.festpass.local";
    }

    @Transactional(readOnly = true)
    public Integrity integrity(long eventId) {
        return new Integrity(
                count("""
                        SELECT count(*) FROM (SELECT seat_id FROM tickets WHERE event_id = ? AND seat_id IS NOT NULL
                                              GROUP BY seat_id HAVING count(*) > 1) x""", eventId),
                count("SELECT count(*) FROM ticket_tiers WHERE event_id = ? AND reserved > capacity", eventId),
                count("""
                        SELECT count(*) FROM ticket_tiers tt
                        WHERE tt.event_id = ? AND (SELECT count(*) FROM tickets t WHERE t.tier_id = tt.id) > tt.capacity""",
                        eventId),
                count("""
                        SELECT count(*) FROM orders o
                        WHERE o.event_id = ? AND o.status = 'PAID'
                          AND NOT EXISTS (SELECT 1 FROM tickets t WHERE t.order_id = o.id)""", eventId),
                count("""
                        SELECT count(*) FROM seats s
                        WHERE s.event_id = ? AND s.status = 'BOOKED'
                          AND NOT EXISTS (SELECT 1 FROM tickets t WHERE t.seat_id = s.id)""", eventId));
    }

    @Transactional(readOnly = true)
    public Totals totals(Event event) {
        long capacity = count("SELECT COALESCE(SUM(capacity), 0) FROM ticket_tiers WHERE event_id = ?", event.getId());
        long sold = count("SELECT count(*) FROM tickets WHERE event_id = ?", event.getId());
        long held = event.getSeatingMode() == SeatingMode.SEATED
                ? count("SELECT count(*) FROM seats WHERE event_id = ? AND status = 'HELD'", event.getId())
                : count("""
                        SELECT COALESCE(SUM(quantity), 0) FROM holds
                        WHERE event_id = ? AND status = 'ACTIVE'""", event.getId());
        return new Totals(capacity, sold, held);
    }

    /**
     * Deletes every sandbox event (its tiers, seats, holds, orders and tickets go with it, by cascade),
     * every bot account and every run record. Real events and real users are not touched.
     *
     * @return {sandboxEvents, bots}
     */
    @Transactional
    public long[] reset() {
        jdbc.update("DELETE FROM arena_runs");
        long sandboxEvents = jdbc.update("DELETE FROM events WHERE is_sandbox");
        long bots = jdbc.update("DELETE FROM users WHERE is_bot");
        return new long[]{sandboxEvents, bots};
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }
}
