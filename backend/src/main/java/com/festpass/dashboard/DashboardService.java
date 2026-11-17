package com.festpass.dashboard;

import com.festpass.auth.AppUserPrincipal;
import com.festpass.event.Event;
import com.festpass.event.EventDtos.TierView;
import com.festpass.event.EventEnums.EventStatus;
import com.festpass.event.EventEnums.SeatingMode;
import com.festpass.event.EventService;
import com.festpass.event.TicketTier;
import com.festpass.event.TicketTierRepository;
import com.festpass.realtime.ViewerTracker;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Numbers for the organizer's "mission control" screen: sales so far, sales over time,
 * how full each tier is, which seats sold first, and check-in progress.
 * The page reloads this whenever the event's live stats change.
 */
@Service
public class DashboardService {

    public record TierStat(Long tierId, String name, String colorHex, long pricePaise, int capacity,
                           long sold, long held, long revenuePaise) {}

    /** Tickets sold in one time bucket, and the running total up to its end. */
    public record SalesPoint(Instant at, long tickets, long cumulative) {}

    /** {@code order} runs from 0 (the first seat sold) to 1 (the latest). */
    public record SeatHeat(Long seatId, double order) {}

    public record Dashboard(
            Long eventId, String slug, String title, EventStatus status, SeatingMode seatingMode,
            Instant saleOpensAt, Instant startsAt, int viewers, long capacity, long ticketsSold, long ticketsHeld,
            long revenuePaise, long checkedIn, long soldLastMinute, String bucket,
            List<TierStat> tiers, List<SalesPoint> salesOverTime, List<SeatHeat> seatHeat) {}

    private final EventService eventService;
    private final TicketTierRepository tiers;
    private final ViewerTracker viewers;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public DashboardService(EventService eventService, TicketTierRepository tiers, ViewerTracker viewers,
                            JdbcTemplate jdbc, Clock clock) {
        this.eventService = eventService;
        this.tiers = tiers;
        this.viewers = viewers;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Dashboard forEvent(Long eventId, AppUserPrincipal actor) {
        Event event = eventService.requireManageable(eventId, actor);
        Instant now = Instant.now(clock);

        Map<Long, Long> soldByTier = new HashMap<>();
        jdbc.query("SELECT tier_id, count(*) FROM tickets WHERE event_id = ? GROUP BY tier_id",
                rs -> { soldByTier.put(rs.getLong(1), rs.getLong(2)); }, eventId);

        // "Remaining" already treats expired holds as free, so held = capacity - sold - remaining.
        Map<Long, Integer> remainingByTier = new HashMap<>();
        for (TierView view : eventService.tierViews(event)) {
            remainingByTier.put(view.id(), view.remaining());
        }

        List<TierStat> tierStats = new ArrayList<>();
        for (TicketTier tier : tiers.findByEventIdOrderByIdAsc(eventId)) {
            long sold = soldByTier.getOrDefault(tier.getId(), 0L);
            long held = Math.max(0, tier.getCapacity() - sold - remainingByTier.getOrDefault(tier.getId(), 0));
            tierStats.add(new TierStat(tier.getId(), tier.getName(), tier.getColorHex(), tier.getPricePaise(),
                    tier.getCapacity(), sold, held, sold * tier.getPricePaise()));
        }

        List<Instant> saleTimes = jdbc.query(
                "SELECT created_at FROM tickets WHERE event_id = ? ORDER BY created_at",
                (rs, i) -> rs.getTimestamp(1).toInstant(), eventId);
        ChronoUnit bucket = bucketFor(saleTimes, now);

        List<SeatHeat> heat = new ArrayList<>();
        List<Long> seatsInSaleOrder = jdbc.queryForList(
                "SELECT seat_id FROM tickets WHERE event_id = ? AND seat_id IS NOT NULL ORDER BY created_at, id",
                Long.class, eventId);
        for (int i = 0; i < seatsInSaleOrder.size(); i++) {
            heat.add(new SeatHeat(seatsInSaleOrder.get(i),
                    seatsInSaleOrder.size() == 1 ? 0 : (double) i / (seatsInSaleOrder.size() - 1)));
        }

        Long checkedIn = jdbc.queryForObject(
                "SELECT count(*) FROM tickets WHERE event_id = ? AND checked_in_at IS NOT NULL", Long.class, eventId);
        Long soldLastMinute = jdbc.queryForObject(
                "SELECT count(*) FROM tickets WHERE event_id = ? AND created_at > ?", Long.class,
                eventId, Timestamp.from(now.minus(Duration.ofMinutes(1))));

        return new Dashboard(event.getId(), event.getSlug(), event.getTitle(), event.getStatus(), event.getSeatingMode(),
                event.getSaleOpensAt(), event.getStartsAt(), viewers.viewers(eventId),
                tierStats.stream().mapToLong(TierStat::capacity).sum(),
                tierStats.stream().mapToLong(TierStat::sold).sum(),
                tierStats.stream().mapToLong(TierStat::held).sum(),
                tierStats.stream().mapToLong(TierStat::revenuePaise).sum(),
                checkedIn == null ? 0 : checkedIn, soldLastMinute == null ? 0 : soldLastMinute,
                bucket.name(), tierStats, salesOverTime(saleTimes, bucket, now), heat);
    }

    /** Minutes for a sale that is a few hours old, hours up to a few days, then days. */
    private static ChronoUnit bucketFor(List<Instant> saleTimes, Instant now) {
        if (saleTimes.isEmpty()) {
            return ChronoUnit.MINUTES;
        }
        Duration span = Duration.between(saleTimes.get(0), now);
        if (span.compareTo(Duration.ofHours(3)) <= 0) {
            return ChronoUnit.MINUTES;
        }
        return span.compareTo(Duration.ofDays(5)) <= 0 ? ChronoUnit.HOURS : ChronoUnit.DAYS;
    }

    /** One point per bucket from the first sale to now, including empty buckets, so the line is honest. */
    private static List<SalesPoint> salesOverTime(List<Instant> saleTimes, ChronoUnit bucket, Instant now) {
        if (saleTimes.isEmpty()) {
            return List.of();
        }
        TreeMap<Instant, Long> perBucket = new TreeMap<>();
        for (Instant at : saleTimes) {
            perBucket.merge(at.truncatedTo(bucket), 1L, Long::sum);
        }
        List<SalesPoint> points = new ArrayList<>();
        long cumulative = 0;
        Instant last = now.truncatedTo(bucket);
        // A leading zero point, so the first sale shows as a rise rather than a lone dot.
        points.add(new SalesPoint(perBucket.firstKey().minus(1, bucket), 0, 0));
        for (Instant at = perBucket.firstKey(); !at.isAfter(last); at = at.plus(1, bucket)) {
            long tickets = perBucket.getOrDefault(at, 0L);
            cumulative += tickets;
            points.add(new SalesPoint(at, tickets, cumulative));
        }
        return points;
    }
}
