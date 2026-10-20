package com.festpass.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.festpass.auth.AppUserPrincipal;
import com.festpass.user.Role;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class OrderPaymentIntegrationTest extends BookingTestSupport {

    @Test
    void sameOrderAndPaymentKeysTwiceGiveOneOrderAndOneSetOfTickets() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long eventId = publishedSeatedEvent(organizer, "Idempotent");
        AppUserPrincipal buyer = student();
        String holdId = read(holdSeats(eventId, buyer, seatIds(eventId).subList(0, 2))).get("holdId").asText();

        String orderKey = newKey();
        JsonNode first = read(createOrder(holdId, buyer, orderKey).andExpect(status().isCreated()));
        JsonNode again = read(createOrder(holdId, buyer, orderKey).andExpect(status().isOk()));
        assertThat(again.get("id").asLong()).isEqualTo(first.get("id").asLong());
        // A new key for the same hold still returns the one order for that hold.
        JsonNode otherKey = read(createOrder(holdId, buyer, newKey()).andExpect(status().isOk()));
        assertThat(otherKey.get("id").asLong()).isEqualTo(first.get("id").asLong());
        assertThat(first.get("amountPaise").asLong()).isEqualTo(2 * 29_900);
        assertThat(first.get("status").asText()).isEqualTo("PENDING");

        long orderId = first.get("id").asLong();
        String payKey = newKey();
        JsonNode paid = read(pay(orderId, buyer, "SUCCESS", payKey).andExpect(status().isOk()));
        JsonNode paidAgain = read(pay(orderId, buyer, "SUCCESS", payKey).andExpect(status().isOk()));
        assertThat(paid.get("status").asText()).isEqualTo("PAID");
        assertThat(paid.get("ticketIds")).hasSize(2);
        assertThat(paidAgain.get("ticketIds")).isEqualTo(paid.get("ticketIds"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders WHERE hold_id = ?::uuid", Long.class, holdId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tickets WHERE order_id = ?", Long.class, orderId))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_attempts WHERE order_id = ?", Long.class, orderId))
                .isEqualTo(1);
        assertThat(seatStatus(seatIds(eventId).get(0))).isEqualTo("BOOKED");
        assertThat(jdbc.queryForObject("SELECT status FROM holds WHERE id = ?::uuid", String.class, holdId))
                .isEqualTo("CONVERTED");
    }

    @Test
    void idempotencyKeysCannotBeReusedForSomethingElse() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long eventId = publishedSeatedEvent(organizer, "Key Reuse");
        List<Long> seats = seatIds(eventId);
        AppUserPrincipal buyer = student();
        String holdA = read(holdSeats(eventId, buyer, List.of(seats.get(0)))).get("holdId").asText();
        String holdB = read(holdSeats(eventId, buyer, List.of(seats.get(1)))).get("holdId").asText();

        String key = newKey();
        createOrder(holdA, buyer, key).andExpect(status().isCreated());
        createOrder(holdB, buyer, key)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        createOrder(holdB, buyer, "short").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['Idempotency-Key']").exists());
    }

    @Test
    void declinedPaymentCanBeRetried() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long[] ga = publishedGaEvent(organizer, "Declined", 50);
        AppUserPrincipal buyer = student();
        String holdId = read(holdGa(ga[0], buyer, ga[1], 2)).get("holdId").asText();
        long orderId = read(createOrder(holdId, buyer, newKey())).get("id").asLong();

        String failKey = newKey();
        pay(orderId, buyer, "FAIL", failKey)
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("PAYMENT_DECLINED"))
                .andExpect(jsonPath("$.orderStatus").value("PENDING"));
        pay(orderId, buyer, "SUCCESS", failKey).andExpect(status().isPaymentRequired()); // same key, same answer

        pay(orderId, buyer, "SUCCESS", newKey())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketIds.length()").value(2));
        assertThat(jdbc.queryForObject("SELECT reserved FROM ticket_tiers WHERE id = ?", Integer.class, ga[1]))
                .isEqualTo(2);
    }

    @Test
    void onlyTheBuyerCanSeeOrPayTheOrder() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long eventId = publishedSeatedEvent(organizer, "Not Yours");
        AppUserPrincipal buyer = student();
        AppUserPrincipal stranger = student();
        String holdId = read(holdSeats(eventId, buyer, List.of(seatIds(eventId).get(0)))).get("holdId").asText();

        createOrder(holdId, stranger, newKey()).andExpect(status().isNotFound());
        long orderId = read(createOrder(holdId, buyer, newKey())).get("id").asLong();

        call(get("/api/orders/" + orderId), stranger, null).andExpect(status().isNotFound());
        pay(orderId, stranger, "SUCCESS", newKey()).andExpect(status().isNotFound());
        call(get("/api/orders/" + orderId), buyer, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].seat.row").value("A"));
    }
}
