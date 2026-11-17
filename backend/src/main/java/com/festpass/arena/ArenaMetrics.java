package com.festpass.arena;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Counters the bots update while they fight. Thread-safe; read by the 250 ms progress ticker. */
final class ArenaMetrics {

    private static final Logger log = LoggerFactory.getLogger(ArenaMetrics.class);

    final LongAdder botsLoggedIn = new LongAdder();
    final LongAdder botsFinished = new LongAdder();
    final LongAdder holdAttempts = new LongAdder();
    final LongAdder holdsWon = new LongAdder();
    final LongAdder conflicts = new LongAdder();
    final LongAdder rateLimited = new LongAdder();
    final LongAdder notAdmitted = new LongAdder();
    final LongAdder ordersPaid = new LongAdder();
    final LongAdder ticketsBought = new LongAdder();
    final LongAdder paymentsFailed = new LongAdder();
    final LongAdder abandoned = new LongAdder();
    final LongAdder holdsExpiredAtPayment = new LongAdder();
    final LongAdder errors = new LongAdder();

    private final List<Long> holdLatencies = new ArrayList<>();

    /** What went wrong, by step and code, e.g. "order:HOLD_EXPIRED" or "hold:IO-ConnectException" (no answer). */
    final Map<String, LongAdder> errorsByCode = new ConcurrentHashMap<>();

    void error(String step, BotHttp.Reply reply) {
        errors.increment();
        String code = reply.code().isEmpty() ? "HTTP" + reply.status() : reply.code();
        errorsByCode.computeIfAbsent(step + ":" + code, k -> {
            log.warn("Arena bot error {}: HTTP {} {}", k, reply.status(), reply.body());
            return new LongAdder();
        }).increment();
    }

    void holdLatency(long millis) {
        synchronized (holdLatencies) {
            holdLatencies.add(millis);
        }
    }

    /** {p50, p95} of hold-request latency in ms, or {0, 0} before the first request. */
    long[] percentiles() {
        long[] sorted;
        synchronized (holdLatencies) {
            sorted = holdLatencies.stream().mapToLong(Long::longValue).toArray();
        }
        if (sorted.length == 0) {
            return new long[]{0, 0};
        }
        java.util.Arrays.sort(sorted);
        return new long[]{sorted[index(sorted.length, 0.50)], sorted[index(sorted.length, 0.95)]};
    }

    /** Nearest-rank percentile. */
    private static int index(int size, double percentile) {
        return Math.min(size - 1, Math.max(0, (int) Math.ceil(percentile * size) - 1));
    }
}
