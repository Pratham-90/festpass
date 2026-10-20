package com.festpass.booking;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL advisory locks used by the booking engine. */
@Component
public class BookingLocks {

    private final JdbcTemplate jdbc;

    public BookingLocks(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Serialises one user's hold requests for one event until the current transaction ends,
     * so two parallel requests can't both pass the per-user ticket limit check (PLAN.md 5.3).
     * Other users are not blocked.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockUserForEvent(Long userId, Long eventId) {
        jdbc.query("SELECT pg_advisory_xact_lock(?, ?)", rs -> null,
                Math.toIntExact(userId), Math.toIntExact(eventId));
    }
}
