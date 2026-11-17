package com.festpass.ticket;

import com.festpass.auth.AppUserPrincipal;
import com.festpass.event.EventEnums.EventCategory;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Fest Passport (PLAN.md section 8, feature 7): one stamp for every event the student
 * was checked in to, plus a few simple milestones.
 */
@Service
public class PassportService {

    /** How many check-ins in row A make someone a "front row regular". */
    static final int FRONT_ROW_TARGET = 3;

    public record Stamp(Long eventId, String eventSlug, String eventTitle, EventCategory category, String venueName,
                        Instant startsAt, Instant checkedInAt, String tierName, String color, boolean frontRow) {}

    public record Milestone(String code, String title, String description, int progress, int target, boolean earned) {}

    public record Passport(int eventsAttended, List<Stamp> stamps, List<Milestone> milestones) {}

    private final JdbcTemplate jdbc;

    public PassportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Passport forUser(AppUserPrincipal user) {
        // One row per event: the first check-in, and whether any checked-in seat was in row A.
        List<Stamp> stamps = jdbc.query("""
                SELECT e.id, e.slug, e.title, e.category, e.venue_name, e.starts_at,
                       min(t.checked_in_at) AS first_checkin,
                       (array_agg(tier.name ORDER BY t.checked_in_at))[1] AS tier_name,
                       (array_agg(tier.color_hex ORDER BY t.checked_in_at))[1] AS color,
                       bool_or(s.row_label = 'A') AS front_row
                FROM tickets t
                JOIN orders o ON o.id = t.order_id
                JOIN events e ON e.id = t.event_id
                JOIN ticket_tiers tier ON tier.id = t.tier_id
                LEFT JOIN seats s ON s.id = t.seat_id
                WHERE o.user_id = ? AND t.checked_in_at IS NOT NULL AND e.is_sandbox = FALSE
                GROUP BY e.id
                ORDER BY first_checkin""",
                (rs, i) -> new Stamp(rs.getLong("id"), rs.getString("slug"), rs.getString("title"),
                        EventCategory.valueOf(rs.getString("category")), rs.getString("venue_name"),
                        rs.getTimestamp("starts_at").toInstant(), rs.getTimestamp("first_checkin").toInstant(),
                        rs.getString("tier_name"), rs.getString("color"), rs.getBoolean("front_row")),
                user.id());

        int attended = stamps.size();
        int frontRows = (int) stamps.stream().filter(Stamp::frontRow).count();
        List<Milestone> milestones = List.of(
                milestone("FIRST_EVENT", "First fest", "Get checked in at your first event.", attended, 1),
                milestone("FIVE_EVENTS", "High five", "Attend five events.", attended, 5),
                milestone("FRONT_ROW_REGULAR", "Front row regular",
                        "Sit in row A at " + FRONT_ROW_TARGET + " events.", frontRows, FRONT_ROW_TARGET));
        return new Passport(attended, stamps, milestones);
    }

    private static Milestone milestone(String code, String title, String description, int progress, int target) {
        return new Milestone(code, title, description, Math.min(progress, target), target, progress >= target);
    }
}
