package com.festpass.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.festpass.auth.AppUserPrincipal;
import com.festpass.user.Role;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;

/** Expiry is driven by moving the test clock, never by sleeping. */
class HoldExpiryIntegrationTest extends BookingTestSupport {

    @Autowired
    HoldExpiryJob expiryJob;

    @Test
    void anExpiredHoldFreesTheSeatAndCanNeverBecomeATicket() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long eventId = publishedSeatedEvent(organizer, "Expiry");
        long seat = seatIds(eventId).get(0);
        AppUserPrincipal slow = student();
        String holdId = read(holdSeats(eventId, slow, List.of(seat))).get("holdId").asText();
        long orderId = read(createOrder(holdId, slow, newKey())).get("id").asLong();

        clock.advance(Duration.ofMinutes(5).plusSeconds(1));

        // Free straight away, before the expiry job has run.
        assertThat(seatMap(eventId).get("seats").get(0).get("status").asText()).isEqualTo("AVAILABLE");
        AppUserPrincipal fast = student();
        holdSeats(eventId, fast, List.of(seat)).andExpect(status().isCreated());

        // Paying for the old order now fails, and nobody gets the seat twice.
        pay(orderId, slow, "SUCCESS", newKey())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HOLD_EXPIRED"))
                .andExpect(jsonPath("$.orderStatus").value("FAILED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tickets WHERE order_id = ?", Long.class, orderId)).isZero();
        assertThat(seatStatus(seat)).isEqualTo("HELD"); // still held by the fast user
        assertThat(jdbc.queryForObject("SELECT status FROM holds WHERE id = ?::uuid", String.class, holdId))
                .isEqualTo("EXPIRED");
    }

    @Test
    void anOrderCannotBeCreatedForAnExpiredHold() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long eventId = publishedSeatedEvent(organizer, "Late Order");
        AppUserPrincipal user = student();
        String holdId = read(holdSeats(eventId, user, List.of(seatIds(eventId).get(0)))).get("holdId").asText();

        clock.advance(Duration.ofMinutes(6));
        createOrder(holdId, user, newKey())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HOLD_EXPIRED"));
    }

    @Test
    void theExpiryJobGivesSeatsAndGaTicketsBack() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long eventId = publishedSeatedEvent(organizer, "Job Seats");
        long[] ga = publishedGaEvent(organizer, "Job GA", 20);
        List<Long> seats = seatIds(eventId).subList(0, 3);
        AppUserPrincipal user = student();
        String seatHold = read(holdSeats(eventId, user, seats)).get("holdId").asText();
        String gaHold = read(holdGa(ga[0], user, ga[1], 4)).get("holdId").asText();

        expiryJob.expireDue();
        assertThat(seatStatus(seats.get(0))).isEqualTo("HELD"); // not expired yet

        clock.advance(Duration.ofMinutes(5).plusSeconds(1));
        assertThat(expiryJob.expireDue()).isGreaterThanOrEqualTo(2);

        seats.forEach(id -> assertThat(seatStatus(id)).isEqualTo("AVAILABLE"));
        assertThat(jdbc.queryForObject("SELECT reserved FROM ticket_tiers WHERE id = ?", Integer.class, ga[1])).isZero();
        assertThat(jdbc.queryForList("SELECT status FROM holds WHERE id IN (?::uuid, ?::uuid)", String.class,
                seatHold, gaHold)).containsOnly("EXPIRED");

        // Running it again changes nothing: a hold is never given back twice.
        assertThat(expiryJob.expireDue()).isZero();
        assertThat(jdbc.queryForObject("SELECT reserved FROM ticket_tiers WHERE id = ?", Integer.class, ga[1])).isZero();
    }

    @Test
    void cancellingAfterTheJobExpiredTheHoldChangesNothing() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long[] ga = publishedGaEvent(organizer, "Expire Then Cancel", 10);
        AppUserPrincipal user = student();
        String holdId = read(holdGa(ga[0], user, ga[1], 2)).get("holdId").asText();
        clock.advance(Duration.ofMinutes(3));
        holdGa(ga[0], student(), ga[1], 3).andExpect(status().isCreated()); // expires 3 minutes later

        clock.advance(Duration.ofMinutes(3)); // first hold expired, second still live
        expiryJob.expireDue();
        call(delete("/api/holds/" + holdId), user, null).andExpect(status().isNoContent());

        // The expired hold's 2 tickets were given back exactly once; the live hold keeps its 3.
        assertThat(jdbc.queryForObject("SELECT reserved FROM ticket_tiers WHERE id = ?", Integer.class, ga[1]))
                .isEqualTo(3);
    }
}
