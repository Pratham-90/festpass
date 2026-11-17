package com.festpass.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.festpass.auth.AppUserPrincipal;
import com.festpass.booking.BookingTestSupport;
import com.festpass.user.Role;
import com.festpass.user.User;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class DashboardAndPassportIntegrationTest extends BookingTestSupport {

    @Test
    void dashboardShowsSalesHoldsRevenueAndWhichSeatsSoldFirst() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long eventId = publishedSeatedEvent(organizer, "Mission Control"); // 10 seats at 299
        List<Long> seats = seatIds(eventId);

        buySeats(eventId, student(), List.of(seats.get(4)));
        clock.advance(Duration.ofMinutes(2));
        buySeats(eventId, student(), List.of(seats.get(0), seats.get(1)));
        holdSeats(eventId, student(), List.of(seats.get(9))).andExpect(status().isCreated());

        JsonNode d = read(mvc.perform(get("/api/organizer/events/" + eventId + "/dashboard").session(organizer))
                .andExpect(status().isOk()));
        assertThat(d.get("ticketsSold").asInt()).isEqualTo(3);
        assertThat(d.get("ticketsHeld").asInt()).isEqualTo(1);
        assertThat(d.get("capacity").asInt()).isEqualTo(10);
        assertThat(d.get("revenuePaise").asLong()).isEqualTo(3 * 29_900);
        assertThat(d.get("soldLastMinute").asInt()).isEqualTo(2);
        assertThat(d.get("tiers").get(0).get("sold").asInt()).isEqualTo(3);
        assertThat(d.get("tiers").get(0).get("held").asInt()).isEqualTo(1);

        // Sales over time: a zero lead-in, then one bucket per minute, ending at the running total.
        JsonNode sales = d.get("salesOverTime");
        assertThat(d.get("bucket").asText()).isEqualTo("MINUTES");
        assertThat(sales.get(0).get("cumulative").asInt()).isZero();
        assertThat(sales.get(sales.size() - 1).get("cumulative").asInt()).isEqualTo(3);

        // The heat map orders seats by when they sold: seat 4 went first.
        JsonNode heat = d.get("seatHeat");
        assertThat(heat).hasSize(3);
        assertThat(heat.get(0).get("seatId").asLong()).isEqualTo(seats.get(4));
        assertThat(heat.get(0).get("order").asDouble()).isZero();
        assertThat(heat.get(2).get("order").asDouble()).isEqualTo(1.0);
    }

    @Test
    void dashboardIsOnlyForTheEventsOrganizerOrAnAdmin() throws Exception {
        MockHttpSession owner = loginAs(Role.ORGANIZER);
        long[] ga = publishedGaEvent(owner, "Private Numbers", 20);
        String url = "/api/organizer/events/" + ga[0] + "/dashboard";

        mvc.perform(get(url).session(loginAs(Role.ORGANIZER))).andExpect(status().isForbidden());
        call(get(url), student(), null).andExpect(status().isForbidden());
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
        mvc.perform(get(url).session(loginAs(Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketsSold").value(0))
                .andExpect(jsonPath("$.salesOverTime.length()").value(0));
    }

    @Test
    void passportGetsAStampPerCheckedInEventAndTracksMilestones() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        AppUserPrincipal fan = student();
        AppUserPrincipal admin = new AppUserPrincipal(createUser(Role.ADMIN));

        call(get("/api/me/passport"), fan, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventsAttended").value(0))
                .andExpect(jsonPath("$.stamps.length()").value(0))
                .andExpect(jsonPath("$.milestones[0].earned").value(false));

        // Two tickets for one event (both scanned) and one for another: two stamps, not three.
        long first = publishedSeatedEvent(organizer, "Passport One");
        long second = publishedSeatedEvent(organizer, "Passport Two");
        checkIn(first, fan, admin, seatIds(first).subList(0, 2)); // row A
        clock.advance(Duration.ofHours(1));
        checkIn(second, fan, admin, List.of(seatIds(second).get(7))); // row B
        // A ticket that was bought but never scanned earns nothing.
        long skipped = publishedSeatedEvent(organizer, "Passport Skipped");
        buySeats(skipped, fan, List.of(seatIds(skipped).get(0)));

        JsonNode passport = read(call(get("/api/me/passport"), fan, null).andExpect(status().isOk()));
        assertThat(passport.get("eventsAttended").asInt()).isEqualTo(2);
        assertThat(passport.get("stamps").get(0).get("eventTitle").asText()).isEqualTo("Passport One");
        assertThat(passport.get("stamps").get(0).get("frontRow").asBoolean()).isTrue();
        assertThat(passport.get("stamps").get(1).get("frontRow").asBoolean()).isFalse();

        JsonNode milestones = passport.get("milestones");
        assertThat(milestones.get(0).get("code").asText()).isEqualTo("FIRST_EVENT");
        assertThat(milestones.get(0).get("earned").asBoolean()).isTrue();
        assertThat(milestones.get(1).get("progress").asInt()).isEqualTo(2);
        assertThat(milestones.get(1).get("earned").asBoolean()).isFalse();
        assertThat(milestones.get(2).get("progress").asInt()).isEqualTo(1);

        // Someone else's passport is their own.
        call(get("/api/me/passport"), student(), null).andExpect(jsonPath("$.eventsAttended").value(0));
    }

    @Test
    void gateScreenListsOnlyAssignedEvents() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long mine = publishedSeatedEvent(organizer, "My Gate");
        publishedSeatedEvent(organizer, "Someone Else's Gate");
        User gate = createUser(Role.GATE_STAFF);
        postJson("/api/organizer/events/" + mine + "/gate-staff", Map.of("email", gate.getEmail()), organizer)
                .andExpect(status().isOk());
        buySeats(mine, student(), List.of(seatIds(mine).get(0)));

        call(get("/api/checkin/events"), new AppUserPrincipal(gate), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("My Gate"))
                .andExpect(jsonPath("$[0].total").value(1))
                .andExpect(jsonPath("$[0].checkedIn").value(0));
        call(get("/api/checkin/events"), student(), null).andExpect(status().isForbidden());
    }

    /** Buys the seats and scans every ticket at the gate. */
    private void checkIn(long eventId, AppUserPrincipal buyer, AppUserPrincipal staff, List<Long> seats) throws Exception {
        JsonNode order = buySeats(eventId, buyer, seats);
        for (JsonNode ticketId : order.get("ticketIds")) {
            String code = read(call(get("/api/tickets/" + ticketId.asText()), buyer, null)).get("code").asText();
            call(post("/api/checkin"), staff, Map.of("eventId", eventId, "code", code))
                    .andExpect(jsonPath("$.result").value("VALID"));
        }
    }
}
