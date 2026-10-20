package com.festpass.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.festpass.event.EventTestSupport;
import com.festpass.user.Role;
import com.festpass.user.User;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;

/**
 * The database's own safety nets, tested with raw SQL that bypasses the service layer.
 * Even if a bug slipped into the Java code, these constraints would stop overselling.
 */
class SchemaGuaranteesIntegrationTest extends EventTestSupport {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void aSeatCanNeverHaveTwoTickets() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long eventId = publishedSeatedEvent(organizer, "Schema Seats");
        JsonNode seat = read(mvc.perform(get("/api/events/" + eventId + "/seatmap"))).get("seats").get(0);
        long seatId = seat.get("id").asLong();
        long tierId = seat.get("tierId").asLong();
        User buyer = createUser(Role.STUDENT);

        long firstOrder = insertOrder(buyer.getId(), eventId);
        long secondOrder = insertOrder(buyer.getId(), eventId);
        insertTicket(firstOrder, eventId, tierId, seatId);

        assertThatThrownBy(() -> insertTicket(secondOrder, eventId, tierId, seatId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("tickets_seat_unique_idx");

        // General-admission tickets (no seat) are not limited by the index.
        insertTicket(firstOrder, eventId, tierId, null);
        insertTicket(secondOrder, eventId, tierId, null);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tickets WHERE seat_id = ?", Long.class, seatId))
                .isEqualTo(1);
    }

    @Test
    void aTierCanNeverReserveMoreThanItsCapacity() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long eventId = createEvent(organizer, "Schema GA", "GENERAL").get("id").asLong();
        long tierId = addGaTier(organizer, eventId, "GA", 100, 5);

        jdbc.update("UPDATE ticket_tiers SET reserved = 5 WHERE id = ?", tierId);
        assertThatThrownBy(() -> jdbc.update("UPDATE ticket_tiers SET reserved = 6 WHERE id = ?", tierId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE ticket_tiers SET reserved = -1 WHERE id = ?", tierId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anAvailableSeatCannotPointAtAHold() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long eventId = publishedSeatedEvent(organizer, "Schema Holds");
        long seatId = read(mvc.perform(get("/api/events/" + eventId + "/seatmap"))).get("seats").get(0).get("id").asLong();

        assertThatThrownBy(() -> jdbc.update("UPDATE seats SET status = 'HELD' WHERE id = ?", seatId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long insertOrder(long userId, long eventId) {
        UUID holdId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO holds (id, event_id, user_id, status, expires_at, created_at)
                VALUES (?, ?, ?, 'CONVERTED', ?, ?)""", holdId, eventId, userId, now, now);
        return jdbc.queryForObject("""
                INSERT INTO orders (user_id, event_id, hold_id, idempotency_key, amount_paise, status, created_at)
                VALUES (?, ?, ?, ?, 100, 'PAID', ?) RETURNING id""",
                Long.class, userId, eventId, holdId, UUID.randomUUID().toString(), now);
    }

    private void insertTicket(long orderId, long eventId, long tierId, Long seatId) {
        jdbc.update("""
                INSERT INTO tickets (id, order_id, event_id, tier_id, seat_id, holder_name, created_at)
                VALUES (?, ?, ?, ?, ?, 'Test', ?)""",
                UUID.randomUUID(), orderId, eventId, tierId, seatId, Timestamp.from(Instant.now()));
    }
}
