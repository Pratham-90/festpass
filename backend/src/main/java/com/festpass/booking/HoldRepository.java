package com.festpass.booking;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface HoldRepository extends JpaRepository<Hold, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Hold h where h.id = :id")
    Optional<Hold> findByIdForUpdate(UUID id);

    /**
     * Ends an active hold (EXPIRED or RELEASED). Returns 1 only for the caller that actually ended it,
     * so the expiry job, a user cancelling and a late payment can never free the same hold twice.
     */
    @Modifying(flushAutomatically = true)
    @Query(nativeQuery = true, value = "UPDATE holds SET status = :to WHERE id = :id AND status = 'ACTIVE'")
    int end(UUID id, String to);

    /** Turns an active, unexpired hold into a purchase. 0 means it expired or ended meanwhile. */
    @Modifying(flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE holds SET status = 'CONVERTED'
            WHERE id = :id AND status = 'ACTIVE' AND expires_at > :now""")
    int convert(UUID id, Instant now);

    @Query(nativeQuery = true, value = """
            SELECT id FROM holds
            WHERE status = 'ACTIVE' AND expires_at < :now
            ORDER BY expires_at
            LIMIT :limit""")
    List<UUID> findExpiredIds(Instant now, int limit);

    /** Tickets the user is currently holding for an event (unexpired active holds). */
    @Query(nativeQuery = true, value = """
            SELECT COALESCE(SUM(quantity), 0) FROM holds
            WHERE user_id = :userId AND event_id = :eventId AND status = 'ACTIVE' AND expires_at > :now""")
    int countActiveTickets(Long userId, Long eventId, Instant now);
}
