package com.festpass.booking;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.festpass.auth.AppUserPrincipal;
import com.festpass.event.EventTestSupport;
import com.festpass.user.Role;
import com.festpass.user.User;
import com.festpass.user.UserRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Helpers for booking tests: many users quickly, holds, orders and payments through the real API. */
public abstract class BookingTestSupport extends EventTestSupport {

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected UserRepository users;

    /** One real BCrypt hash, reused so creating hundreds of test users stays fast. */
    private static String passwordHash;

    /** Students inserted directly with SQL (BCrypt for each would take seconds per hundred users). */
    protected List<AppUserPrincipal> students(int count) {
        if (passwordHash == null) {
            passwordHash = users.findByEmail(createUser(Role.STUDENT).getEmail()).orElseThrow().getPasswordHash();
        }
        String batch = UUID.randomUUID().toString().substring(0, 8);
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(new Object[]{"Bot " + i, "bot-" + batch + "-" + i + "@test.local", passwordHash, Timestamp.from(Instant.now())});
        }
        jdbc.batchUpdate("INSERT INTO users (name, email, password_hash, role, created_at) VALUES (?, ?, ?, 'STUDENT', ?)", rows);
        List<User> created = jdbc.queryForList("SELECT id FROM users WHERE email LIKE ? ORDER BY id", Long.class,
                "bot-" + batch + "-%").stream().map(id -> users.findById(id).orElseThrow()).toList();
        return created.stream().map(AppUserPrincipal::new).toList();
    }

    protected AppUserPrincipal student() {
        return students(1).get(0);
    }

    /** Acts as the user without going through login. */
    protected static RequestPostProcessor as(AppUserPrincipal user) {
        return authentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    protected ResultActions call(MockHttpServletRequestBuilder request, AppUserPrincipal user, Object body) throws Exception {
        request.with(as(user)).with(csrf());
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json(body));
        }
        return mvc.perform(request);
    }

    protected ResultActions holdSeats(long eventId, AppUserPrincipal user, List<Long> seatIds) throws Exception {
        return call(post("/api/events/" + eventId + "/holds"), user, Map.of("seatIds", seatIds));
    }

    protected ResultActions holdGa(long eventId, AppUserPrincipal user, long tierId, int quantity) throws Exception {
        return call(post("/api/events/" + eventId + "/holds"), user, Map.of("tierId", tierId, "quantity", quantity));
    }

    protected ResultActions createOrder(String holdId, AppUserPrincipal user, String key) throws Exception {
        return call(post("/api/orders").header("Idempotency-Key", key), user, Map.of("holdId", holdId));
    }

    protected ResultActions pay(long orderId, AppUserPrincipal user, String outcome, String key) throws Exception {
        return call(post("/api/payments/mock/" + orderId).header("Idempotency-Key", key), user, Map.of("outcome", outcome));
    }

    /** Hold the seats, order and pay. Returns the paid order. */
    protected JsonNode buySeats(long eventId, AppUserPrincipal user, List<Long> seatIds) throws Exception {
        String holdId = read(holdSeats(eventId, user, seatIds).andExpect(status().isCreated())).get("holdId").asText();
        long orderId = read(createOrder(holdId, user, newKey()).andExpect(status().isCreated())).get("id").asLong();
        return read(pay(orderId, user, "SUCCESS", newKey()).andExpect(status().isOk()));
    }

    protected static String newKey() {
        return UUID.randomUUID().toString();
    }

    protected JsonNode seatMap(long eventId) throws Exception {
        return read(mvc.perform(get("/api/events/" + eventId + "/seatmap")).andExpect(status().isOk()));
    }

    protected List<Long> seatIds(long eventId) throws Exception {
        List<Long> ids = new ArrayList<>();
        seatMap(eventId).get("seats").forEach(s -> ids.add(s.get("id").asLong()));
        return ids;
    }

    protected String seatStatus(long seatId) {
        return jdbc.queryForObject("SELECT status FROM seats WHERE id = ?", String.class, seatId);
    }

    /** A published GA event with one tier. Returns {eventId, tierId}. */
    protected long[] publishedGaEvent(MockHttpSession organizer, String title, int capacity) throws Exception {
        long eventId = createEvent(organizer, title, "GENERAL").get("id").asLong();
        long tierId = addGaTier(organizer, eventId, "General", 19_900, capacity);
        publish(organizer, eventId).andExpect(status().isOk());
        return new long[]{eventId, tierId};
    }

    /**
     * Runs all tasks at the same moment: every thread waits at a start latch, then they all go.
     * Returns the results in task order.
     */
    protected static <T> List<T> runTogether(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            CountDownLatch ready = new CountDownLatch(tasks.size());
            CountDownLatch go = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            ready.await();
            go.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> f : futures) {
                results.add(f.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
