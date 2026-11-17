package com.festpass.arena;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.festpass.RealHttpCsrf;
import com.festpass.arena.ArenaDtos.RunView;
import com.festpass.arena.ArenaDtos.StartRunRequest;
import com.festpass.auth.AppUserPrincipal;
import com.festpass.booking.BookingTestSupport;
import com.festpass.user.Role;
import com.festpass.user.UserService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.csrf.CsrfTokenRepository;

/**
 * The Stress Arena against a real server on a random port: hundreds of bots on virtual threads
 * calling the real HTTP API at the same moment, then the integrity checks.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ArenaIntegrationTest extends BookingTestSupport {

    @Autowired ArenaService arena;
    @Autowired FilterChainProxy securityFilters;
    @Autowired CsrfTokenRepository csrfTokenRepository;
    @Autowired UserService users;

    /** A published seated event with one tier: rows × seatsPerRow seats. */
    private long seatedEvent(MockHttpSession organizer, String title, int rows, int seatsPerRow) throws Exception {
        long eventId = createEvent(organizer, title, "SEATED").get("id").asLong();
        long tierId = addSeatedTier(organizer, eventId, "Standard", 29_900);
        addSection(organizer, eventId, "Hall", tierId, rows, seatsPerRow, List.of(seatsPerRow / 2))
                .andExpect(status().isCreated());
        publish(organizer, eventId).andExpect(status().isOk());
        return eventId;
    }

    /** Everything about an event that the arena must never change. */
    private String snapshot(long eventId) {
        return jdbc.queryForList("SELECT status, count(*) AS n FROM seats WHERE event_id = ? GROUP BY status ORDER BY status", eventId)
                + " tiers=" + jdbc.queryForList("SELECT id, capacity, reserved FROM ticket_tiers WHERE event_id = ? ORDER BY id", eventId)
                + " tickets=" + jdbc.queryForObject("SELECT count(*) FROM tickets WHERE event_id = ?", Long.class, eventId)
                + " holds=" + jdbc.queryForObject("SELECT count(*) FROM holds WHERE event_id = ?", Long.class, eventId);
    }

    /** Starts a run exactly as the admin page does, after giving real HTTP its CSRF cookies back. */
    private JsonNode runToTheEnd(long eventId, int bots, ArenaRun.Strategy strategy) throws Exception {
        AppUserPrincipal admin = new AppUserPrincipal(createUser(Role.ADMIN));
        RealHttpCsrf.restore(securityFilters, csrfTokenRepository);
        RunView run = arena.start(new StartRunRequest(eventId, bots, strategy), admin);

        // Wait for the background run to finish: poll its status (this is not waiting for an expiry).
        Instant deadline = Instant.now().plus(Duration.ofMinutes(3));
        while (Instant.now().isBefore(deadline)) {
            RunView now = arena.get(run.id());
            if (now.status() == ArenaRun.Status.FINISHED || now.status() == ArenaRun.Status.FAILED) {
                assertThat(now.status()).as("run result: %s", now.result()).isEqualTo(ArenaRun.Status.FINISHED);
                return now.result();
            }
            Thread.sleep(250);
        }
        throw new AssertionError("The arena run did not finish within 3 minutes");
    }

    @Test
    void fiveHundredBotsRushFiftySeatsInUnderThirtySecondsAndNothingIsOversold() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long eventId = seatedEvent(organizer, "Arena Fifty", 5, 10);
        String before = snapshot(eventId);

        JsonNode r = runToTheEnd(eventId, 500, ArenaRun.Strategy.RUSH_SAME_SEATS);

        assertThat(r.get("botsLoggedIn").asInt()).isEqualTo(500);
        assertThat(r.get("raceMs").asLong()).isLessThan(30_000);
        assertThat(r.get("allZero").asBoolean()).isTrue();
        r.get("integrity").fields().forEachRemaining(check ->
                assertThat(check.getValue().asLong()).as(check.getKey()).isZero());
        assertThat(r.get("capacity").asInt()).isEqualTo(50);
        assertThat(r.get("ticketsSold").asInt() + r.get("ticketsHeld").asInt()).isLessThanOrEqualTo(50);
        assertThat(r.get("ticketsSold").asInt()).isEqualTo(r.get("ticketsBought").asInt());
        assertThat(r.get("conflicts").asInt()).isPositive(); // they really did fight over the same seats
        assertThat(r.get("errors").asInt()).isZero();

        assertThat(snapshot(eventId)).as("the real event is untouched").isEqualTo(before);
    }

    @Test
    void aThousandBotsRushAHundredGaTicketsAndExactlyAHundredAreAllocated() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long eventId = createEvent(organizer, "Arena GA", "GENERAL").get("id").asLong();
        addGaTier(organizer, eventId, "Fan Pit", 59_900, 100);
        addGaTier(organizer, eventId, "General", 29_900, 300);
        publish(organizer, eventId).andExpect(status().isOk());
        String before = snapshot(eventId);

        JsonNode r = runToTheEnd(eventId, 1000, ArenaRun.Strategy.GA_RUSH);

        assertThat(r.get("allZero").asBoolean()).isTrue();
        long sandbox = r.get("sandboxEventId").asLong();
        Map<String, Object> fanPit = jdbc.queryForMap(
                "SELECT capacity, reserved FROM ticket_tiers WHERE event_id = ? AND name = 'Fan Pit'", sandbox);
        long fanPitTickets = jdbc.queryForObject(
                "SELECT count(*) FROM tickets t JOIN ticket_tiers tt ON tt.id = t.tier_id WHERE t.event_id = ? AND tt.name = 'Fan Pit'",
                Long.class, sandbox);
        // Every one of the 100 is taken, and never one more: sold, or still held by bots that abandoned checkout.
        assertThat(((Number) fanPit.get("reserved")).intValue()).isEqualTo(100);
        assertThat(fanPitTickets).isLessThanOrEqualTo(100);
        assertThat(fanPitTickets + r.get("ticketsHeld").asLong()).isEqualTo(100);
        assertThat(r.get("ticketsSold").asLong()).isEqualTo(fanPitTickets);
        assertThat(r.get("errors").asInt()).isZero();

        assertThat(snapshot(eventId)).as("the real event is untouched").isEqualTo(before);
    }

    @Test
    void sandboxesAndBotsStayHiddenAndResetRemovesThem() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long eventId = seatedEvent(organizer, "Arena Hidden", 2, 5);
        JsonNode r = runToTheEnd(eventId, 40, ArenaRun.Strategy.RANDOM_SEATS);
        long sandbox = r.get("sandboxEventId").asLong();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE is_bot", Long.class)).isGreaterThanOrEqualTo(40);
        assertThat(users.list(null)).noneMatch(u -> u.email().endsWith("@arena.festpass.local"));
        assertThat(users.list("bot")).noneMatch(u -> u.email().endsWith("@arena.festpass.local"));
        mvc.perform(get("/api/events")).andExpect(jsonPath("$[?(@.id == " + sandbox + ")]").isEmpty());
        mvc.perform(get("/api/events/" + sandbox + "/seatmap")).andExpect(status().isNotFound());

        arena.reset();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM events WHERE is_sandbox", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE is_bot", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM arena_runs", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM events WHERE id = ?", Long.class, eventId)).isEqualTo(1);
    }

    @Test
    void onlyAdminsCanUseTheArena() throws Exception {
        call(get("/api/admin/arena/runs"), student(), null).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/arena/runs").session(loginAs(Role.ORGANIZER))).andExpect(status().isForbidden());

        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long seated = seatedEvent(organizer, "Arena Rules", 1, 4);
        MockHttpSession admin = loginAs(Role.ADMIN);
        postJson("/api/admin/arena/runs", Map.of("sourceEventId", seated, "bots", 10, "strategy", "GA_RUSH"), admin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.strategy").exists());
        postJson("/api/admin/arena/runs", Map.of("sourceEventId", seated, "bots", 5000, "strategy", "RANDOM_SEATS"), admin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.bots").exists());
    }
}
