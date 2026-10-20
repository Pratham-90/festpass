package com.festpass.event;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TicketTierRepository extends JpaRepository<TicketTier, Long> {

    List<TicketTier> findByEventIdOrderByIdAsc(Long eventId);

    List<TicketTier> findByEventIdIn(Collection<Long> eventIds);

    boolean existsByEventIdAndNameIgnoreCase(Long eventId, String name);

    long countByEventId(Long eventId);

    /**
     * Seated tiers: capacity is the number of seats, so it grows when a section is generated.
     * Clears the persistence context so tiers loaded earlier in the transaction are re-read.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update TicketTier t set t.capacity = t.capacity + :seats where t.id = :tierId")
    int addCapacity(Long tierId, int seats);

    /**
     * General admission: reserves tickets only if they fit. 0 rows means sold out.
     * The row lock makes concurrent reservations queue up, and each re-checks the capacity.
     */
    @Modifying(flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE ticket_tiers SET reserved = reserved + :quantity
            WHERE id = :tierId AND event_id = :eventId AND reserved + :quantity <= capacity""")
    int reserve(Long tierId, Long eventId, int quantity);

    @Modifying(flushAutomatically = true)
    @Query(nativeQuery = true, value = "UPDATE ticket_tiers SET reserved = reserved - :quantity WHERE id = :tierId")
    int unreserve(Long tierId, int quantity);
}
