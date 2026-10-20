package com.festpass.ticket;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    List<Ticket> findByOrderIdOrderByCreatedAtAscIdAsc(Long orderId);

    @Query("select t from Ticket t where t.orderId in (select o.id from Order o where o.userId = :userId)")
    List<Ticket> findOwnedBy(Long userId);

    @Query(nativeQuery = true, value = """
            SELECT count(*) FROM tickets t JOIN orders o ON o.id = t.order_id
            WHERE o.user_id = :userId AND t.event_id = :eventId""")
    int countOwned(Long userId, Long eventId);

    /**
     * Check-in as one conditional update: only the first scan of a ticket changes a row.
     * Clears the persistence context so a following read sees who checked the ticket in.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE tickets SET checked_in_at = :now, checked_in_by = :staffId
            WHERE id = :ticketId AND event_id = :eventId AND checked_in_at IS NULL""")
    int checkIn(UUID ticketId, Long eventId, Instant now, Long staffId);

    long countByEventId(Long eventId);

    long countByEventIdAndCheckedInAtIsNotNull(Long eventId);
}
