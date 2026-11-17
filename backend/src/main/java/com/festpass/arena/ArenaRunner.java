package com.festpass.arena;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.festpass.arena.ArenaDtos.Progress;
import com.festpass.arena.ArenaSandbox.Bot;
import com.festpass.arena.ArenaSandbox.Integrity;
import com.festpass.arena.ArenaSandbox.Totals;
import com.festpass.arena.BotHttp.Reply;
import com.festpass.arena.BotHttp.Session;
import com.festpass.event.Event;
import com.festpass.event.EventRepository;
import com.festpass.event.TicketTier;
import com.festpass.event.TicketTierRepository;
import com.festpass.queue.WaitingRoom;
import com.festpass.realtime.RealtimePublisher;
import com.festpass.seating.Seat;
import com.festpass.seating.SeatRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Runs one Stress Arena fight (PLAN.md section 8, feature 8):
 * <ol>
 *   <li>copy the chosen event into a hidden sandbox event;</li>
 *   <li>create the bot accounts and log every bot in <em>before</em> the start, because BCrypt is slow;</li>
 *   <li>release all bots at the same instant (virtual threads behind a CountDownLatch). Each bot calls the
 *       real HTTP API: join the queue if it's a flash drop, hold, order, pay. About 10% give up:
 *       half fail the payment, half abandon their checkout so the hold has to expire;</li>
 *   <li>publish progress every 250 ms, then run the integrity checks and publish the report.</li>
 * </ol>
 */
@Component
public class ArenaRunner {

    private static final Logger log = LoggerFactory.getLogger(ArenaRunner.class);
    private static final int MAX_HOLD_ATTEMPTS = 5;  // the per-second rate limit allows exactly 5
    private static final int LOGIN_CONCURRENCY = 64;

    private final ArenaSandbox sandbox;
    private final ArenaRunRepository runs;
    private final EventRepository events;
    private final TicketTierRepository tiers;
    private final SeatRepository seats;
    private final WaitingRoom waitingRoom;
    private final RealtimePublisher publisher;
    private final ObjectMapper json;
    private final Environment environment;
    private final Clock clock;

    public ArenaRunner(ArenaSandbox sandbox, ArenaRunRepository runs, EventRepository events, TicketTierRepository tiers,
                       SeatRepository seats, WaitingRoom waitingRoom, RealtimePublisher publisher, ObjectMapper json,
                       Environment environment, Clock clock) {
        this.sandbox = sandbox;
        this.runs = runs;
        this.events = events;
        this.tiers = tiers;
        this.seats = seats;
        this.waitingRoom = waitingRoom;
        this.publisher = publisher;
        this.json = json;
        this.environment = environment;
        this.clock = clock;
    }

    /** What every bot needs to know about the sandbox. */
    private record Target(Event event, ArenaRun.Strategy strategy, List<Long> seatsBestFirst, Long gaTierId) {}

    /** Mutable run state shared with the progress ticker. */
    private static final class State {
        final ArenaMetrics metrics = new ArenaMetrics();
        volatile ArenaRun.Status status = ArenaRun.Status.PREPARING;
        volatile String phase = "Copying the event";
        volatile Long sandboxEventId;
        volatile long raceStartNanos;
        volatile long raceEndNanos;
        volatile JsonNode report;
    }

    public void run(long runId, long sourceEventId, long adminId) {
        ArenaRun run = runs.findById(runId).orElseThrow();
        State state = new State();
        ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "arena-progress-" + runId);
            t.setDaemon(true);
            return t;
        });
        ticker.scheduleAtFixedRate(() -> publish(run, state), 0, 250, TimeUnit.MILLISECONDS);

        try (BotHttp http = new BotHttp(baseUrl(), json)) {
            Event source = events.findById(sourceEventId).orElseThrow();
            Event copy = sandbox.cloneEvent(source, runId, adminId);
            state.sandboxEventId = copy.getId();
            run.setSandboxEventId(copy.getId());
            runs.save(run);

            state.phase = "Creating bots";
            // Bots get a low BCrypt cost: their logins then take about a millisecond each.
            String password = UUID.randomUUID().toString();
            List<Bot> bots = sandbox.createBots(runId, run.getBots(), new BCryptPasswordEncoder(4).encode(password));

            state.phase = "Logging bots in";
            List<Session> sessions = logIn(http, bots, password, state.metrics);
            Target target = target(copy, run.getStrategy());

            state.phase = "Fight!";
            state.status = ArenaRun.Status.RUNNING;
            run.setStatus(ArenaRun.Status.RUNNING);
            run.setStartedAt(Instant.now(clock));
            runs.save(run);
            race(runId, sessions, target, state);

            state.phase = "Checking the results";
            Integrity integrity = sandbox.integrity(copy.getId());
            Totals totals = sandbox.totals(copy);
            state.report = report(run, source, target, state, integrity, totals);
            finish(run, ArenaRun.Status.FINISHED, state.report);
            state.status = ArenaRun.Status.FINISHED;
            state.phase = integrity.allZero() ? "Done: nothing oversold" : "Done: PROBLEMS FOUND";
            log.info("Arena run {} finished: {}", runId, state.report);
        } catch (Exception e) {
            log.error("Arena run {} failed", runId, e);
            ObjectNode error = json.createObjectNode().put("error", String.valueOf(e.getMessage()));
            state.report = error;
            state.status = ArenaRun.Status.FAILED;
            state.phase = "Failed";
            finish(run, ArenaRun.Status.FAILED, error);
        } finally {
            ticker.shutdownNow();
            publish(run, state); // the final message, with the report
        }
    }

    private void finish(ArenaRun run, ArenaRun.Status status, JsonNode result) {
        run.setStatus(status);
        run.setFinishedAt(Instant.now(clock));
        run.setResultJson(result.toString());
        runs.save(run);
    }

    /** Each bot gets a CSRF cookie and logs in, a few dozen at a time. Done before the start on purpose. */
    private List<Session> logIn(BotHttp http, List<Bot> bots, String password, ArenaMetrics metrics) throws InterruptedException {
        List<Session> sessions = new ArrayList<>(bots.size());
        Semaphore permits = new Semaphore(LOGIN_CONCURRENCY);
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Bot bot : bots) {
                Session session = http.newSession();
                sessions.add(session);
                pool.submit(() -> {
                    permits.acquireUninterruptibly();
                    try {
                        session.get("/health");
                        Reply login = session.post("/auth/login", Map.of("email", bot.email(), "password", password), Map.of());
                        if (login.ok()) {
                            metrics.botsLoggedIn.increment();
                        }
                    } finally {
                        permits.release();
                    }
                });
            }
        }
        return sessions;
    }

    private Target target(Event copy, ArenaRun.Strategy strategy) {
        if (strategy == ArenaRun.Strategy.GA_RUSH) {
            // Everyone rushes the first tier (for the sample open-air event that's the 100-ticket Fan Pit).
            Long tierId = tiers.findByEventIdOrderByIdAsc(copy.getId()).get(0).getId();
            return new Target(copy, strategy, List.of(), tierId);
        }
        // "Best" seats first: front rows, then closest to the centre.
        List<Long> best = seats.findForMap(copy.getId()).stream()
                .sorted(Comparator.comparingInt(Seat::getY).thenComparingInt(s -> Math.abs(s.getX())))
                .map(Seat::getId)
                .toList();
        return new Target(copy, strategy, best, null);
    }

    /** Releases every bot at the same moment and waits for all of them to finish. */
    private void race(long runId, List<Session> sessions, Target target, State state) throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(sessions.size());
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        for (int i = 0; i < sessions.size(); i++) {
            Session session = sessions.get(i);
            int botIndex = i;
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    bot(runId, botIndex, session, target, state.metrics);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException e) {
                    state.metrics.errors.increment();
                    state.metrics.errorsByCode.computeIfAbsent("exception:" + e.getClass().getSimpleName(),
                            k -> new java.util.concurrent.atomic.LongAdder()).increment();
                } finally {
                    state.metrics.botsFinished.increment();
                }
            });
        }
        ready.await();
        state.raceStartNanos = System.nanoTime();
        go.countDown();

        // A flash drop's sandbox lets everyone in as soon as they've joined (real fans wait for batches).
        if (target.event().isFlashDrop()) {
            while (state.metrics.botsFinished.sum() < sessions.size()) {
                waitingRoom.admitEveryone(target.event().getId());
                Thread.sleep(100);
            }
        }
        pool.shutdown();
        if (!pool.awaitTermination(5, TimeUnit.MINUTES)) {
            pool.shutdownNow();
            throw new IllegalStateException("Bots were still running after 5 minutes");
        }
        state.raceEndNanos = System.nanoTime();
    }

    /** One bot: (queue) → hold → order → pay, the way a real student would. */
    private void bot(long runId, int index, Session s, Target target, ArenaMetrics m) throws InterruptedException {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        long eventId = target.event().getId();
        Map<String, String> admission = Map.of();

        if (target.event().isFlashDrop()) {
            s.post("/events/" + eventId + "/queue", null, Map.of());
            String token = null;
            for (int poll = 0; poll < 100 && token == null; poll++) {
                Reply status = s.get("/events/" + eventId + "/queue/me");
                if ("ADMITTED".equals(status.body().path("status").asText())) {
                    token = status.body().path("admissionToken").asText();
                } else {
                    Thread.sleep(100);
                }
            }
            if (token == null) {
                m.notAdmitted.increment();
                return;
            }
            admission = Map.of("X-Admission-Token", token);
        }

        boolean ga = target.strategy() == ArenaRun.Strategy.GA_RUSH;
        int quantity = ga ? 1 : Math.min(target.event().getMaxTicketsPerUser(), random.nextInt(100) < 70 ? 1 : 2);
        Set<Long> knownTaken = new HashSet<>();
        JsonNode hold = null;

        for (int attempt = 0; attempt < MAX_HOLD_ATTEMPTS && hold == null; attempt++) {
            Object body;
            if (ga) {
                body = Map.of("tierId", target.gaTierId(), "quantity", quantity);
            } else {
                List<Long> pick = pickSeats(target, quantity, attempt, knownTaken, random);
                if (pick.isEmpty()) {
                    break; // every seat this bot knows of is taken
                }
                body = Map.of("seatIds", pick);
            }
            Reply reply = s.post("/events/" + eventId + "/holds", body, admission);
            m.holdAttempts.increment();
            m.holdLatency(reply.millis());
            if (reply.status() == 201) {
                hold = reply.body();
                m.holdsWon.increment();
            } else if ("SEAT_UNAVAILABLE".equals(reply.code())) {
                m.conflicts.increment();
                reply.body().path("unavailableSeatIds").forEach(id -> knownTaken.add(id.asLong()));
            } else if ("SOLD_OUT".equals(reply.code())) {
                // Like a fan refreshing: tickets come back when someone else's payment fails.
                m.conflicts.increment();
                Thread.sleep(150 + random.nextInt(150));
            } else if (reply.status() == 429) {
                m.rateLimited.increment();
                return;
            } else {
                m.error("hold", reply);
                return;
            }
        }
        if (hold == null) {
            return;
        }

        String holdId = hold.path("holdId").asText();
        Reply order = s.post("/orders", Map.of("holdId", holdId), Map.of("Idempotency-Key", "arena-" + runId + "-" + index));
        if (!order.ok()) {
            m.error("order", order);
            return;
        }
        long orderId = order.body().path("id").asLong();

        int roll = random.nextInt(100);
        if (roll < 5) {
            m.abandoned.increment(); // closes the tab: the hold expires on its own later
            return;
        }
        if (roll < 10) {
            s.post("/payments/mock/" + orderId, Map.of("outcome", "FAIL"),
                    Map.of("Idempotency-Key", "arena-pay-" + runId + "-" + index));
            m.paymentsFailed.increment();
            s.delete("/holds/" + holdId); // gives up and lets the tickets go
            return;
        }
        Reply paid = s.post("/payments/mock/" + orderId, Map.of("outcome", "SUCCESS"),
                Map.of("Idempotency-Key", "arena-pay-" + runId + "-" + index));
        if (paid.ok()) {
            m.ordersPaid.increment();
            m.ticketsBought.add(paid.body().path("ticketIds").size());
        } else if ("HOLD_EXPIRED".equals(paid.code())) {
            m.holdsExpiredAtPayment.increment();
        } else {
            m.error("pay", paid);
        }
    }

    /**
     * RUSH_SAME_SEATS: the first try goes for the very best seats, the same ones for every bot; after each
     * conflict the bot looks a little further back (front half, then three quarters, then anywhere).
     * RANDOM_SEATS: random seats from the whole map.
     */
    private static List<Long> pickSeats(Target target, int quantity, int attempt, Set<Long> knownTaken,
                                        ThreadLocalRandom random) {
        List<Long> candidates = target.seatsBestFirst().stream().filter(id -> !knownTaken.contains(id)).toList();
        if (candidates.size() < quantity) {
            return candidates.isEmpty() ? List.of() : List.of(candidates.get(0));
        }
        if (target.strategy() == ArenaRun.Strategy.RUSH_SAME_SEATS && attempt == 0) {
            return candidates.subList(0, quantity);
        }
        int pool = target.strategy() == ArenaRun.Strategy.RUSH_SAME_SEATS
                ? Math.min(candidates.size(), Math.max(quantity, candidates.size() * (attempt + 1) / 4))
                : candidates.size();
        List<Long> picked = new ArrayList<>(quantity);
        while (picked.size() < quantity) {
            Long id = candidates.get(random.nextInt(pool));
            if (!picked.contains(id)) {
                picked.add(id);
            }
        }
        return picked;
    }

    private JsonNode report(ArenaRun run, Event source, Target target, State state, Integrity integrity, Totals totals) {
        ArenaMetrics m = state.metrics;
        long[] p = m.percentiles();
        ObjectNode report = json.createObjectNode();
        report.put("sourceEventId", source.getId());
        report.put("sourceEventTitle", source.getTitle());
        report.put("sandboxEventId", target.event().getId());
        report.put("strategy", run.getStrategy().name());
        report.put("bots", run.getBots());
        report.put("botsLoggedIn", m.botsLoggedIn.sum());
        report.put("raceMs", (state.raceEndNanos - state.raceStartNanos) / 1_000_000);
        report.put("capacity", totals.capacity());
        report.put("ticketsSold", totals.ticketsSold());
        report.put("ticketsHeld", totals.ticketsHeld());
        report.put("holdAttempts", m.holdAttempts.sum());
        report.put("holdsWon", m.holdsWon.sum());
        report.put("conflicts", m.conflicts.sum());
        report.put("rateLimited", m.rateLimited.sum());
        report.put("notAdmitted", m.notAdmitted.sum());
        report.put("ordersPaid", m.ordersPaid.sum());
        report.put("ticketsBought", m.ticketsBought.sum());
        report.put("paymentsFailed", m.paymentsFailed.sum());
        report.put("abandoned", m.abandoned.sum());
        report.put("holdsExpiredAtPayment", m.holdsExpiredAtPayment.sum());
        report.put("errors", m.errors.sum());
        ObjectNode errorCodes = report.putObject("errorsByCode");
        m.errorsByCode.forEach((code, n) -> errorCodes.put(code, n.sum()));
        report.put("p50Ms", p[0]);
        report.put("p95Ms", p[1]);
        ObjectNode checks = report.putObject("integrity");
        checks.put("seatsWithSeveralTickets", integrity.seatsWithSeveralTickets());
        checks.put("tiersReservedOverCapacity", integrity.tiersReservedOverCapacity());
        checks.put("tiersWithMoreTicketsThanCapacity", integrity.tiersWithMoreTicketsThanCapacity());
        checks.put("paidOrdersWithoutTickets", integrity.paidOrdersWithoutTickets());
        checks.put("bookedSeatsWithoutTicket", integrity.bookedSeatsWithoutTicket());
        report.put("allZero", integrity.allZero());
        return report;
    }

    private void publish(ArenaRun run, State state) {
        try {
            ArenaMetrics m = state.metrics;
            long[] p = m.percentiles();
            long elapsed = state.raceStartNanos == 0 ? 0
                    : ((state.raceEndNanos == 0 ? System.nanoTime() : state.raceEndNanos) - state.raceStartNanos) / 1_000_000;
            publisher.toTopic("/topic/arena/" + run.getId(), new Progress(
                    run.getId(), state.status, state.phase, state.sandboxEventId, run.getBots(),
                    m.botsLoggedIn.sum(), m.botsFinished.sum(), elapsed,
                    m.holdAttempts.sum(), m.holdsWon.sum(), m.conflicts.sum(), m.rateLimited.sum(), m.notAdmitted.sum(),
                    m.ordersPaid.sum(), m.ticketsBought.sum(), m.paymentsFailed.sum(), m.abandoned.sum(), m.errors.sum(),
                    p[0], p[1], state.report));
        } catch (RuntimeException e) {
            log.debug("Arena progress publish failed", e);
        }
    }

    /** This same server, over the IPv4 loopback address. */
    private String baseUrl() {
        String port = environment.getProperty("local.server.port", environment.getProperty("server.port", "8080"));
        boolean https = environment.getProperty("server.ssl.enabled", Boolean.class, false);
        return (https ? "https" : "http") + "://127.0.0.1:" + port;
    }
}
