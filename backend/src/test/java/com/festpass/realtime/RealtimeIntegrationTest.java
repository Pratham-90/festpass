package com.festpass.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.festpass.auth.AppUserPrincipal;
import com.festpass.booking.BookingTestSupport;
import com.festpass.booking.HoldExpiryJob;
import com.festpass.queue.WaitingRoom;
import com.festpass.user.Role;
import com.festpass.user.User;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The real-time layer end to end: a real server on a random port, real STOMP clients over
 * WebSocket, and the same booking API the browser uses.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RealtimeIntegrationTest extends BookingTestSupport {

    @LocalServerPort
    int port;

    @Autowired
    SimpleBrokerMessageHandler broker;

    @Autowired
    HoldExpiryJob expiryJob;

    @Autowired
    WaitingRoom waitingRoom;

    @Autowired
    SimpUserRegistry userRegistry;

    @Autowired
    FilterChainProxy securityFilters;

    @Autowired
    CsrfTokenRepository csrfTokenRepository;

    private StompTestClient visitor() throws Exception {
        return new StompTestClient(port, null, null, objectMapper, broker, userRegistry);
    }

    /** Logs in over real HTTP and connects with that session cookie, as a browser would. */
    private StompTestClient loggedIn(User user) throws Exception {
        restoreCookieCsrf();
        CookieManager cookies = new CookieManager();
        try (HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build()) {
            String base = "http://localhost:" + port;
            http.send(HttpRequest.newBuilder(URI.create(base + "/api/health")).build(), HttpResponse.BodyHandlers.discarding());
            String xsrf = cookie(cookies, "XSRF-TOKEN");
            HttpResponse<String> login = http.send(HttpRequest.newBuilder(URI.create(base + "/api/auth/login"))
                    .header("Content-Type", "application/json")
                    .header("X-XSRF-TOKEN", xsrf)
                    .POST(HttpRequest.BodyPublishers.ofString(json(Map.of("email", user.getEmail(), "password", PASSWORD))))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertThat(login.statusCode()).isEqualTo(200);
        }
        return new StompTestClient(port, "JSESSIONID=" + cookie(cookies, "JSESSIONID"), user.getEmail(),
                objectMapper, broker, userRegistry);
    }

    /**
     * MockMvc's csrf() helper swaps the CSRF filter's cookie repository for a session-based test one
     * (see CsrfCookieFlowIntegrationTest). Put the real one back before logging in over real HTTP.
     */
    private void restoreCookieCsrf() {
        securityFilters.getFilterChains().forEach(chain -> chain.getFilters().stream()
                .filter(CsrfFilter.class::isInstance)
                .forEach(filter -> ReflectionTestUtils.setField(filter, "tokenRepository", csrfTokenRepository)));
    }

    private static String cookie(CookieManager cookies, String name) {
        return cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals(name)).map(HttpCookie::getValue)
                .reduce((first, second) -> second).orElseThrow();
    }

    @Test
    void seatChangesReachOtherBrowsersWhenHeldAndWhenTheHoldExpires() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long eventId = publishedSeatedEvent(organizer, "Live Seats");
        long seat = seatIds(eventId).get(0);
        User buyer = createUser(Role.STUDENT);
        AppUserPrincipal buyerPrincipal = new AppUserPrincipal(buyer);

        try (StompTestClient watcher = visitor(); StompTestClient buyerBrowser = loggedIn(buyer)) {
            BlockingQueue<JsonNode> seats = watcher.subscribe("/topic/events/" + eventId + "/seats");
            BlockingQueue<JsonNode> myHolds = buyerBrowser.subscribe("/user/queue/holds");

            String holdId = read(holdSeats(eventId, buyerPrincipal, List.of(seat)).andExpect(status().isCreated()))
                    .get("holdId").asText();
            JsonNode held = StompTestClient.next(seats, batch -> hasDelta(batch, seat, "HELD"));
            assertThat(held.isArray()).isTrue();

            clock.advance(Duration.ofMinutes(5).plusSeconds(1));
            expiryJob.expireDue();
            StompTestClient.next(seats, batch -> hasDelta(batch, seat, "AVAILABLE"));

            // The buyer's own checkout page is told its hold expired.
            JsonNode update = StompTestClient.next(myHolds, m -> m.get("holdId").asText().equals(holdId));
            assertThat(update.get("status").asText()).isEqualTo("EXPIRED");
        }
    }

    @Test
    void aRolledBackHoldIsNeverAnnounced() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long eventId = publishedSeatedEvent(organizer, "No Phantom Seats");
        List<Long> seats = seatIds(eventId);
        holdSeats(eventId, student(), List.of(seats.get(1))).andExpect(status().isCreated());

        try (StompTestClient watcher = visitor()) {
            BlockingQueue<JsonNode> updates = watcher.subscribe("/topic/events/" + eventId + "/seats");
            // All-or-nothing: seat 0 was free, but seat 1 wasn't, so the whole hold rolls back.
            holdSeats(eventId, student(), List.of(seats.get(0), seats.get(1))).andExpect(status().isConflict());
            holdSeats(eventId, student(), List.of(seats.get(2))).andExpect(status().isCreated());

            JsonNode batch = StompTestClient.next(updates, b -> hasDelta(b, seats.get(2), "HELD"));
            assertThat(hasDelta(batch, seats.get(0), "HELD")).isFalse();
            assertThat(updates.poll(300, TimeUnit.MILLISECONDS)).isNull();
        }
    }

    @Test
    void statsCountViewersAndRemainingTickets() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long[] ga = publishedGaEvent(organizer, "Live Stats", 40);
        String topic = "/topic/events/" + ga[0] + "/stats";

        try (StompTestClient first = visitor(); StompTestClient second = visitor()) {
            BlockingQueue<JsonNode> stats = first.subscribe(topic);
            second.subscribe(topic);
            StompTestClient.next(stats, s -> s.get("viewers").asInt() == 2);

            holdGa(ga[0], student(), ga[1], 3).andExpect(status().isCreated());
            JsonNode afterHold = StompTestClient.next(stats,
                    s -> s.get("remainingByTier").get(String.valueOf(ga[1])).asInt() == 37);
            assertThat(afterHold.get("soldLastMinute").asLong()).isZero();
        }
    }

    @Test
    void admittedFansGetTheirTokenOverWebSocket() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        Map<String, Object> body = eventBody("Live Drop", "GENERAL");
        body.put("flashDrop", true);
        body.put("saleOpensAt", clock.instant().plus(Duration.ofMinutes(1)).toString());
        long eventId = read(postJson("/api/organizer/events", body, organizer)).get("id").asLong();
        long tierId = addGaTier(organizer, eventId, "GA", 100, 50);
        publish(organizer, eventId).andExpect(status().isOk());
        User fan = createUser(Role.STUDENT);
        AppUserPrincipal fanPrincipal = new AppUserPrincipal(fan);

        try (StompTestClient fanBrowser = loggedIn(fan); StompTestClient crowd = visitor()) {
            BlockingQueue<JsonNode> admission = fanBrowser.subscribe("/user/queue/admission");
            BlockingQueue<JsonNode> queue = crowd.subscribe("/topic/events/" + eventId + "/queue");

            call(post("/api/events/" + eventId + "/queue"), fanPrincipal, null).andExpect(status().isOk());
            StompTestClient.next(queue, q -> q.get("waiting").asInt() == 1);

            clock.advance(Duration.ofMinutes(1));
            waitingRoom.admitDue();
            JsonNode token = StompTestClient.next(admission, m -> m.get("eventId").asLong() == eventId);
            StompTestClient.next(queue, q -> q.get("waiting").asInt() == 0 && q.get("admittedSoFar").asInt() == 1);

            call(post("/api/events/" + eventId + "/holds").header("X-Admission-Token", token.get("admissionToken").asText()),
                    fanPrincipal, Map.of("tierId", tierId, "quantity", 2))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    void gateScreensSeeCheckinsLive() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long eventId = publishedSeatedEvent(organizer, "Live Gate");
        User gate = createUser(Role.GATE_STAFF);
        postJson("/api/organizer/events/" + eventId + "/gate-staff", Map.of("email", gate.getEmail()), organizer)
                .andExpect(status().isOk());
        AppUserPrincipal buyer = student();
        String ticketId = buySeats(eventId, buyer, List.of(seatIds(eventId).get(0))).get("ticketIds").get(0).asText();
        String code = read(call(get("/api/tickets/" + ticketId), buyer, null)).get("code").asText();

        try (StompTestClient gateScreen = loggedIn(gate)) {
            BlockingQueue<JsonNode> checkins = gateScreen.subscribe("/topic/events/" + eventId + "/checkins");
            call(post("/api/checkin"), new AppUserPrincipal(gate), Map.of("eventId", eventId, "code", code))
                    .andExpect(status().isOk());

            JsonNode update = StompTestClient.next(checkins, m -> m.get("checkedIn").asInt() == 1);
            assertThat(update.get("total").asInt()).isEqualTo(1);
            assertThat(update.get("lastScan").get("result").asText()).isEqualTo("VALID");
            assertThat(update.get("lastScan").get("seatLabel").asText()).isEqualTo("Hall · Row A · Seat 1");
        }
    }

    @Test
    void browsersCannotPublishOrSnoop() throws Exception {
        try (StompTestClient visitor = visitor()) {
            visitor.subscribeQuietly("/topic/events/1/checkins"); // gate staff only
            assertThat(visitor.errors.poll(5, TimeUnit.SECONDS)).contains("Not allowed");
        }
        try (StompTestClient visitor = visitor()) {
            visitor.subscribeQuietly("/user/queue/holds"); // needs a login
            assertThat(visitor.errors.poll(5, TimeUnit.SECONDS)).contains("Log in");
        }
        try (StompTestClient student = loggedIn(createUser(Role.STUDENT))) {
            student.subscribeQuietly("/topic/arena/1"); // admins only
            assertThat(student.errors.poll(5, TimeUnit.SECONDS)).contains("Not allowed");
        }
        try (StompTestClient visitor = visitor()) {
            visitor.send("/topic/events/1/seats", Map.of("seatId", 1, "status", "AVAILABLE")); // fake update
            assertThat(visitor.errors.poll(5, TimeUnit.SECONDS)).contains("can't send");
        }
    }

    private static boolean hasDelta(JsonNode batch, long seatId, String status) {
        for (JsonNode delta : batch) {
            if (delta.get("seatId").asLong() == seatId && delta.get("status").asText().equals(status)) {
                return true;
            }
        }
        return false;
    }
}
