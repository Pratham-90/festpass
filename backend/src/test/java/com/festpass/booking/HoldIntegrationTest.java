package com.festpass.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.festpass.auth.AppUserPrincipal;
import com.festpass.user.Role;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class HoldIntegrationTest extends BookingTestSupport {

    @Test
    void holdingSeatsMarksThemHeldForEveryone() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long eventId = publishedSeatedEvent(organizer, "Hold Basics");
        List<Long> seats = seatIds(eventId);
        AppUserPrincipal user = student();

        JsonNode hold = read(holdSeats(eventId, user, seats.subList(0, 2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.seats.length()").value(2)));
        assertThat(Instant.parse(hold.get("expiresAt").asText())).isEqualTo(clock.instant().plus(Duration.ofMinutes(5)));

        JsonNode map = seatMap(eventId);
        assertThat(map.get("seats").get(0).get("status").asText()).isEqualTo("HELD");
        assertThat(map.get("tiers").get(0).get("remaining").asInt()).isEqualTo(8);
    }

    @Test
    void holdIsAllOrNothing() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long eventId = publishedSeatedEvent(organizer, "All Or Nothing");
        List<Long> seats = seatIds(eventId);
        holdSeats(eventId, student(), List.of(seats.get(1))).andExpect(status().isCreated());

        holdSeats(eventId, student(), List.of(seats.get(0), seats.get(1), seats.get(2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_UNAVAILABLE"))
                .andExpect(jsonPath("$.unavailableSeatIds.length()").value(1))
                .andExpect(jsonPath("$.unavailableSeatIds[0]").value(seats.get(1)));

        // The free seats in the failed request were not kept.
        assertThat(seatStatus(seats.get(0))).isEqualTo("AVAILABLE");
        assertThat(seatStatus(seats.get(2))).isEqualTo("AVAILABLE");
    }

    @Test
    void seatsFromAnotherEventCannotBeHeld() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long eventA = publishedSeatedEvent(organizer, "Event A");
        long eventB = publishedSeatedEvent(organizer, "Event B");
        long seatOfB = seatIds(eventB).get(0);

        holdSeats(eventA, student(), List.of(seatOfB))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_UNAVAILABLE"));
        assertThat(seatStatus(seatOfB)).isEqualTo("AVAILABLE");
    }

    @Test
    void salesWindowIsEnforced() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        Map<String, Object> body = eventBody("Not Yet", "GENERAL");
        body.put("saleOpensAt", clock.instant().plus(Duration.ofHours(1)).toString());
        long eventId = read(postJson("/api/organizer/events", body, organizer)).get("id").asLong();
        long tierId = addGaTier(organizer, eventId, "GA", 100, 50);
        publish(organizer, eventId);
        AppUserPrincipal user = student();

        holdGa(eventId, user, tierId, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SALE_NOT_OPEN"))
                .andExpect(jsonPath("$.saleOpensAt").exists());

        clock.advance(Duration.ofHours(1));
        holdGa(eventId, user, tierId, 1).andExpect(status().isCreated());
    }

    @Test
    void perUserLimitCountsHoldsAndOwnedTickets() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long eventId = publishedSeatedEvent(organizer, "Limit Test"); // max 4 per user
        List<Long> seats = seatIds(eventId);
        AppUserPrincipal user = student();

        buySeats(eventId, user, seats.subList(0, 2));                          // owns 2
        holdSeats(eventId, user, List.of(seats.get(2))).andExpect(status().isCreated()); // holds 1

        holdSeats(eventId, user, seats.subList(3, 5))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.limit").value(4))
                .andExpect(jsonPath("$.alreadyHeldOrOwned").value(3));
        holdSeats(eventId, user, List.of(seats.get(3))).andExpect(status().isCreated());   // exactly 4
    }

    @Test
    void gaTierSellsOutCleanly() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long[] event = publishedGaEvent(organizer, "Tiny GA", 3);

        holdGa(event[0], student(), event[1], 2).andExpect(status().isCreated());
        holdGa(event[0], student(), event[1], 2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SOLD_OUT"))
                .andExpect(jsonPath("$.remaining").value(1));
        holdGa(event[0], student(), event[1], 1).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT reserved FROM ticket_tiers WHERE id = ?", Integer.class, event[1]))
                .isEqualTo(3);
    }

    @Test
    void rateLimitAllowsFivePerSecond() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long[] event = publishedGaEvent(organizer, "Spammy", 100);
        AppUserPrincipal user = student();

        // Requests that fail validation still count; the limit protects the endpoint itself.
        for (int i = 0; i < 5; i++) {
            holdGa(event[0], user, event[1], 99).andExpect(status().isBadRequest());
        }
        holdGa(event[0], user, event[1], 1)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().exists("Retry-After"));

        clock.advance(Duration.ofSeconds(1));
        holdGa(event[0], user, event[1], 1).andExpect(status().isCreated());
    }

    @Test
    void rateLimitAllowsThirtyPerMinute() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long[] event = publishedGaEvent(organizer, "Patient Spammer", 100);
        AppUserPrincipal user = student();

        for (int i = 0; i < 30; i++) {
            if (i > 0 && i % 5 == 0) {
                clock.advance(Duration.ofSeconds(1));
            }
            holdGa(event[0], user, event[1], 99).andExpect(status().isBadRequest());
        }
        clock.advance(Duration.ofSeconds(1));
        holdGa(event[0], user, event[1], 1).andExpect(status().isTooManyRequests());
    }

    @Test
    void cancellingAHoldFreesTheSeatsOnce() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long[] ga = publishedGaEvent(organizer, "Cancel GA", 10);
        long seatedId = publishedSeatedEvent(organizer, "Cancel Seats");
        long seat = seatIds(seatedId).get(0);
        AppUserPrincipal user = student();

        String seatHold = read(holdSeats(seatedId, user, List.of(seat))).get("holdId").asText();
        String gaHold = read(holdGa(ga[0], user, ga[1], 3)).get("holdId").asText();

        // Someone else can't cancel it.
        call(delete("/api/holds/" + seatHold), student(), null).andExpect(status().isNotFound());

        for (int i = 0; i < 2; i++) { // twice: the second cancel changes nothing
            call(delete("/api/holds/" + seatHold), user, null).andExpect(status().isNoContent());
            call(delete("/api/holds/" + gaHold), user, null).andExpect(status().isNoContent());
        }
        assertThat(seatStatus(seat)).isEqualTo("AVAILABLE");
        assertThat(jdbc.queryForObject("SELECT reserved FROM ticket_tiers WHERE id = ?", Integer.class, ga[1])).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM holds WHERE id = ?::uuid", String.class, seatHold))
                .isEqualTo("RELEASED");
    }

    @Test
    void holdRequestsAreValidated() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long seatedId = publishedSeatedEvent(organizer, "Validation Seats");
        long[] ga = publishedGaEvent(organizer, "Validation GA", 10);
        AppUserPrincipal user = student();
        long seat = seatIds(seatedId).get(0);

        holdSeats(seatedId, user, List.of()).andExpect(jsonPath("$.errors.seatIds").exists());
        holdSeats(seatedId, user, List.of(seat, seat)).andExpect(jsonPath("$.errors.seatIds").exists());
        holdGa(seatedId, user, ga[1], 1).andExpect(jsonPath("$.errors.seatIds").exists());
        holdSeats(ga[0], user, List.of(seat)).andExpect(jsonPath("$.errors.seatIds").exists());
        call(post("/api/events/" + ga[0] + "/holds"), user, Map.of("tierId", ga[1]))
                .andExpect(jsonPath("$.errors.quantity").exists());

        mvc.perform(post("/api/events/" + ga[0] + "/holds")).andExpect(status().isForbidden()); // no CSRF, no login
    }
}
