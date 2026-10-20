package com.festpass.seating;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * Seats. Every status change is a single conditional UPDATE (PLAN.md section 5), so two
 * requests can never both win the same seat: PostgreSQL locks the row for the first updater,
 * and the second re-checks the WHERE clause after the first commits and matches nothing.
 */
public interface SeatRepository extends JpaRepository<Seat, Long> {

    @Query("select s from Seat s where s.eventId = :eventId order by s.y, s.x")
    List<Seat> findForMap(Long eventId);

    @Query("select max(s.y) from Seat s where s.eventId = :eventId")
    Integer findMaxY(Long eventId);

    boolean existsByEventIdAndSectionIgnoreCase(Long eventId, String section);

    long countByEventId(Long eventId);

    List<Seat> findByHoldIdOrderById(UUID holdId);

    /** Free seats per tier. An expired hold counts as free, so this never depends on the expiry job's timing. */
    @Query("""
            select s.tierId as tierId, count(s) as available from Seat s
            where s.eventId = :eventId
              and (s.status = com.festpass.seating.SeatStatus.AVAILABLE
                   or (s.status = com.festpass.seating.SeatStatus.HELD and s.holdExpiresAt < :now))
            group by s.tierId
            """)
    List<TierAvailability> countAvailableByTier(Long eventId, Instant now);

    interface TierAvailability {
        Long getTierId();
        long getAvailable();
    }

    // ---- Booking engine ----

    /** Holds all requested seats that are free (or held by an expired hold). Callers check the count. */
    @Modifying(flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE seats
            SET status = 'HELD', hold_id = :holdId, hold_expires_at = :expiresAt
            WHERE event_id = :eventId
              AND id IN (:seatIds)
              AND (status = 'AVAILABLE' OR (status = 'HELD' AND hold_expires_at < :now))""")
    int hold(UUID holdId, Instant expiresAt, Long eventId, Collection<Long> seatIds, Instant now);

    @Modifying(flushAutomatically = true)
    @Query(nativeQuery = true, value = "UPDATE seats SET status = 'BOOKED' WHERE hold_id = :holdId AND status = 'HELD'")
    int book(UUID holdId);

    /** Frees the seats still held by this hold. Seats already taken over by a newer hold are untouched. */
    @Modifying(flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE seats SET status = 'AVAILABLE', hold_id = NULL, hold_expires_at = NULL
            WHERE hold_id = :holdId AND status = 'HELD'""")
    int free(UUID holdId);

    @Query(nativeQuery = true, value = """
            SELECT COALESCE(SUM(t.price_paise), 0)
            FROM seats s JOIN ticket_tiers t ON t.id = s.tier_id
            WHERE s.hold_id = :holdId""")
    long totalPricePaise(UUID holdId);
}
