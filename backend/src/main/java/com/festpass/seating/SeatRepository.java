package com.festpass.seating;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    @Query("select s from Seat s where s.eventId = :eventId order by s.y, s.x")
    List<Seat> findForMap(Long eventId);

    @Query("select max(s.y) from Seat s where s.eventId = :eventId")
    Integer findMaxY(Long eventId);

    boolean existsByEventIdAndSectionIgnoreCase(Long eventId, String section);

    long countByEventId(Long eventId);

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
}
