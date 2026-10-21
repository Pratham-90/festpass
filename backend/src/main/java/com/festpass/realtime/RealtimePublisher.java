package com.festpass.realtime;

import com.festpass.event.Event;
import com.festpass.event.EventDtos.TierView;
import com.festpass.event.EventRepository;
import com.festpass.event.EventService;
import com.festpass.queue.WaitingRoom;
import com.festpass.realtime.RealtimeMessages.EventStats;
import com.festpass.realtime.RealtimeMessages.SeatDelta;
import com.festpass.seating.SeatStatus;
import com.festpass.ticket.TicketRepository;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Sends live updates to browsers, batched so a rush doesn't flood them (PLAN.md section 7):
 * <ul>
 *   <li>seat deltas per event at most every 100 ms; a seat that changed twice is sent once, with its latest status;</li>
 *   <li>event stats and waiting-room counters at most every 500 ms, and only for events someone is watching;</li>
 *   <li>stats for watched events every 5 s anyway, so "sold in the last minute" and viewers stay current.</li>
 * </ul>
 * Callers only queue things up here; they never block on sending. Everything reaches this class
 * after the database transaction has committed (see RealtimeEventListener).
 */
@Component
public class RealtimePublisher {

    private static final Logger log = LoggerFactory.getLogger(RealtimePublisher.class);

    private final SimpMessagingTemplate messaging;
    private final ViewerTracker viewers;
    private final EventRepository events;
    private final EventService eventService;
    private final TicketRepository tickets;
    private final WaitingRoom waitingRoom;
    private final Clock clock;

    private final Map<Long, Map<Long, SeatStatus>> pendingSeats = new HashMap<>();
    private final Set<Long> dirtyStats = ConcurrentHashMap.newKeySet();
    private final Set<Long> dirtyQueues = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "realtime-publisher");
        thread.setDaemon(true);
        return thread;
    });

    public RealtimePublisher(SimpMessagingTemplate messaging, ViewerTracker viewers, EventRepository events,
                             EventService eventService, TicketRepository tickets, WaitingRoom waitingRoom, Clock clock) {
        this.messaging = messaging;
        this.viewers = viewers;
        this.events = events;
        this.eventService = eventService;
        this.tickets = tickets;
        this.waitingRoom = waitingRoom;
        this.clock = clock;
        ticker.scheduleWithFixedDelay(safely(this::flushSeats), 100, 100, TimeUnit.MILLISECONDS);
        ticker.scheduleWithFixedDelay(safely(this::flushStatsAndQueues), 500, 500, TimeUnit.MILLISECONDS);
        ticker.scheduleWithFixedDelay(safely(() -> dirtyStats.addAll(viewers.watchedEvents())), 5, 5, TimeUnit.SECONDS);
    }

    public void seatsChanged(Long eventId, List<Long> seatIds, SeatStatus status) {
        synchronized (pendingSeats) {
            Map<Long, SeatStatus> forEvent = pendingSeats.computeIfAbsent(eventId, id -> new LinkedHashMap<>());
            seatIds.forEach(seatId -> {
                forEvent.remove(seatId); // re-insert so the batch keeps the order of the latest changes
                forEvent.put(seatId, status);
            });
        }
        dirtyStats.add(eventId);
    }

    public void statsChanged(Long eventId) {
        dirtyStats.add(eventId);
    }

    public void queueChanged(Long eventId) {
        dirtyQueues.add(eventId);
    }

    public void toTopic(String destination, Object payload) {
        messaging.convertAndSend(destination, payload);
    }

    /** {@code username} is the user's login name (their email). */
    public void toUser(String username, String destination, Object payload) {
        messaging.convertAndSendToUser(username, destination, payload);
    }

    void flushSeats() {
        Map<Long, Map<Long, SeatStatus>> batch;
        synchronized (pendingSeats) {
            if (pendingSeats.isEmpty()) {
                return;
            }
            batch = new HashMap<>(pendingSeats);
            pendingSeats.clear();
        }
        batch.forEach((eventId, seats) -> {
            List<SeatDelta> deltas = new ArrayList<>(seats.size());
            seats.forEach((seatId, status) -> deltas.add(new SeatDelta(seatId, status)));
            messaging.convertAndSend(RealtimeMessages.seats(eventId), deltas);
        });
    }

    void flushStatsAndQueues() {
        dirtyStats.addAll(viewers.drainChanged());
        for (Long eventId : drain(dirtyStats)) {
            if (viewers.viewers(eventId) > 0) {
                stats(eventId).ifPresent(stats -> messaging.convertAndSend(RealtimeMessages.stats(eventId), stats));
            }
        }
        for (Long eventId : drain(dirtyQueues)) {
            if (viewers.viewers(eventId) > 0) {
                messaging.convertAndSend(RealtimeMessages.queue(eventId), waitingRoom.snapshot(eventId));
            }
        }
    }

    Optional<EventStats> stats(Long eventId) {
        Optional<Event> event = events.findById(eventId);
        if (event.isEmpty()) {
            return Optional.empty();
        }
        Map<Long, Integer> remaining = eventService.tierViews(event.get()).stream()
                .collect(Collectors.toMap(TierView::id, TierView::remaining, (a, b) -> a, LinkedHashMap::new));
        Instant oneMinuteAgo = Instant.now(clock).minus(Duration.ofMinutes(1));
        long soldLastMinute = tickets.countByEventIdAndCreatedAtAfter(eventId, oneMinuteAgo);
        return Optional.of(new EventStats(eventId, viewers.viewers(eventId), remaining, soldLastMinute));
    }

    private static Set<Long> drain(Set<Long> dirty) {
        Set<Long> drained = new HashSet<>();
        for (Long id : dirty) {
            if (dirty.remove(id)) {
                drained.add(id);
            }
        }
        return drained;
    }

    /** A failing send must not stop future flushes. */
    private static Runnable safely(Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (Exception e) {
                log.warn("Realtime publish failed", e);
            }
        };
    }

    @PreDestroy
    void stop() {
        ticker.shutdownNow();
    }
}
