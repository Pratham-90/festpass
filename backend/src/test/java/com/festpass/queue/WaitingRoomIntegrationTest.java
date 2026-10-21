package com.festpass.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.festpass.auth.AppUserPrincipal;
import com.festpass.booking.BookingTestSupport;
import com.festpass.user.Role;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

class WaitingRoomIntegrationTest extends BookingTestSupport {

    @Autowired
    WaitingRoom waitingRoom;

    /** A seated flash drop (10 seats) whose sale opens in 2 minutes. */
    private long flashDrop(String title) throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        Map<String, Object> body = eventBody(title, "SEATED");
        body.put("flashDrop", true);
        body.put("saleOpensAt", clock.instant().plus(Duration.ofMinutes(2)).toString());
        long eventId = read(postJson("/api/organizer/events", body, organizer)).get("id").asLong();
        long tierId = addSeatedTier(organizer, eventId, "Floor", 39_900);
        addSection(organizer, eventId, "Floor", tierId, 2, 5, List.of()).andExpect(status().isCreated());
        publish(organizer, eventId).andExpect(status().isOk());
        return eventId;
    }

    private ResultActions join(long eventId, AppUserPrincipal user) throws Exception {
        return call(post("/api/events/" + eventId + "/queue"), user, null);
    }

    private JsonNode me(long eventId, AppUserPrincipal user) throws Exception {
        return read(call(get("/api/events/" + eventId + "/queue/me"), user, null).andExpect(status().isOk()));
    }

    private ResultActions holdWithToken(long eventId, AppUserPrincipal user, long seatId, String token) throws Exception {
        var request = post("/api/events/" + eventId + "/holds");
        if (token != null) {
            request.header("X-Admission-Token", token);
        }
        return call(request, user, Map.of("seatIds", List.of(seatId)));
    }

    @Test
    void aFlashDropOnlyAcceptsHoldsFromAdmittedUsers() throws Exception {
        long eventId = flashDrop("Midnight Test Drop");
        long seat = seatIds(eventId).get(0);
        AppUserPrincipal fan = student();
        AppUserPrincipal sneaky = student();

        join(eventId, fan)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.position").value(1))
                .andExpect(jsonPath("$.opensAt").exists());

        holdWithToken(eventId, fan, seat, null).andExpect(jsonPath("$.code").value("SALE_NOT_OPEN"));
        waitingRoom.admitDue();
        assertThat(me(eventId, fan).get("status").asText()).isEqualTo("WAITING"); // nobody is let in early

        clock.advance(Duration.ofMinutes(2));
        holdWithToken(eventId, fan, seat, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_ADMITTED"));

        waitingRoom.admitDue();
        JsonNode admitted = me(eventId, fan);
        assertThat(admitted.get("status").asText()).isEqualTo("ADMITTED");
        String token = admitted.get("admissionToken").asText();

        // The token is personal: someone else can't use it.
        holdWithToken(eventId, sneaky, seat, token).andExpect(jsonPath("$.code").value("NOT_ADMITTED"));
        holdWithToken(eventId, fan, seat, "forged.token.value.x").andExpect(jsonPath("$.code").value("NOT_ADMITTED"));
        holdWithToken(eventId, fan, seat, token).andExpect(status().isCreated());
    }

    @Test
    void theAdmissionWindowRunsOut() throws Exception {
        long eventId = flashDrop("Short Window Drop");
        AppUserPrincipal fan = student();
        join(eventId, fan);
        clock.advance(Duration.ofMinutes(2));
        waitingRoom.admitDue();
        String token = me(eventId, fan).get("admissionToken").asText();

        clock.advance(Duration.ofMinutes(10).plusSeconds(1));
        assertThat(me(eventId, fan).get("status").asText()).isEqualTo("EXPIRED");
        holdWithToken(eventId, fan, seatIds(eventId).get(0), token).andExpect(jsonPath("$.code").value("NOT_ADMITTED"));

        // Joining again puts you at the back of the queue.
        join(eventId, fan).andExpect(jsonPath("$.status").value("WAITING"));
    }

    @Test
    void positionsShrinkAsBatchesAreAdmitted() throws Exception {
        long eventId = flashDrop("Big Queue Drop");
        List<AppUserPrincipal> crowd = students(120);
        for (AppUserPrincipal user : crowd) {
            join(eventId, user).andExpect(status().isOk());
        }
        AppUserPrincipal first = crowd.get(0);
        AppUserPrincipal last = crowd.get(119);
        assertThat(me(eventId, last).get("position").asLong()).isEqualTo(120);
        assertThat(me(eventId, last).get("waiting").asLong()).isEqualTo(120);

        // Joining again keeps your place.
        join(eventId, last).andExpect(jsonPath("$.position").value(120));

        clock.advance(Duration.ofMinutes(2));
        waitingRoom.admitDue(); // batch of 50
        assertThat(me(eventId, first).get("status").asText()).isEqualTo("ADMITTED");
        JsonNode lastAfterOne = me(eventId, last);
        assertThat(lastAfterOne.get("position").asLong()).isEqualTo(70);
        assertThat(lastAfterOne.get("waiting").asLong()).isEqualTo(70);
        assertThat(lastAfterOne.get("admittedSoFar").asLong()).isEqualTo(50);

        waitingRoom.admitDue();
        assertThat(me(eventId, last).get("position").asLong()).isEqualTo(20);

        waitingRoom.admitDue();
        JsonNode lastAfterThree = me(eventId, last);
        assertThat(lastAfterThree.get("status").asText()).isEqualTo("ADMITTED");
        assertThat(lastAfterThree.get("admittedSoFar").asLong()).isEqualTo(120);
        assertThat(lastAfterThree.get("waiting").asLong()).isZero();
    }

    @Test
    void onlyFlashDropsHaveAWaitingRoom() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long normal = publishedSeatedEvent(organizer, "Normal Show");
        join(normal, student())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
        // Normal events don't need an admission token.
        holdWithToken(normal, student(), seatIds(normal).get(0), null).andExpect(status().isCreated());

        mvc.perform(get("/api/events/" + normal + "/queue/me")).andExpect(status().isUnauthorized());
    }
}
