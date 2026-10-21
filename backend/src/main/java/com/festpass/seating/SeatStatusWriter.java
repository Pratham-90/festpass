package com.festpass.seating;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conditional seat updates that report exactly which seats they changed (UPDATE ... RETURNING id),
 * so the live seat map is told about those seats and no others. A seat that another hold has
 * already taken over is not touched, and not reported.
 * Uses JdbcTemplate, which joins the surrounding JPA transaction and connection.
 */
@Component
public class SeatStatusWriter {

    private final JdbcTemplate jdbc;

    public SeatStatusWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** HELD to BOOKED for a paid hold. */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Long> book(UUID holdId) {
        return jdbc.queryForList(
                "UPDATE seats SET status = 'BOOKED' WHERE hold_id = ? AND status = 'HELD' RETURNING id",
                Long.class, holdId);
    }

    /** Frees the seats still held by this hold. */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Long> free(UUID holdId) {
        return jdbc.queryForList("""
                UPDATE seats SET status = 'AVAILABLE', hold_id = NULL, hold_expires_at = NULL
                WHERE hold_id = ? AND status = 'HELD' RETURNING id""", Long.class, holdId);
    }
}
