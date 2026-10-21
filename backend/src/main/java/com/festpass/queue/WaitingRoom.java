package com.festpass.queue;

import com.festpass.common.ApiException;
import com.festpass.common.ErrorCode;
import com.festpass.event.Event;
import com.festpass.event.EventRepository;
import com.festpass.event.EventService;
import com.festpass.queue.QueueEvents.Admitted;
import com.festpass.queue.QueueEvents.QueueChanged;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * The flash-drop waiting room (PLAN.md section 8, feature 3). People join before the sale opens;
 * once it opens, the admission job lets them in in batches, in the order they joined, and each
 * admitted person gets a signed admission token that the hold endpoint requires.
 *
 * <p>The queue lives in memory, which works because FestPass runs as one app instance
 * (see docs/architecture.md). A restart empties the queue but keeps issued tokens valid.
 */
@Service
public class WaitingRoom {

    public enum Status { NOT_IN_QUEUE, WAITING, ADMITTED, EXPIRED }

    /** What one user sees. {@code position} is 1 for the next person in; null unless WAITING. */
    public record QueueStatus(Long eventId, Status status, Long position, long waiting, long admittedSoFar,
                              Instant opensAt, String admissionToken, Instant admissionExpiresAt) {}

    /** What everyone watching the waiting room sees. */
    public record QueueSnapshot(Long eventId, long waiting, long admittedSoFar, Instant opensAt) {}

    private record Admission(String token, Instant expiresAt) {}

    /** One event's queue. Everybody gets a ticket number; numbers are admitted in order. */
    private static final class EventQueue {
        long nextNumber = 1;
        long nextToAdmit = 1;
        long admittedSoFar;
        final TreeMap<Long, Long> waitingByNumber = new TreeMap<>();
        final Map<Long, Long> numberByUser = new HashMap<>();
        final Map<Long, Admission> admissions = new HashMap<>();
    }

    private final Map<Long, EventQueue> queues = new ConcurrentHashMap<>();
    private final EventService eventService;
    private final EventRepository events;
    private final AdmissionTokens tokens;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;
    private final int batchSize;
    private final Duration admissionWindow;

    public WaitingRoom(EventService eventService, EventRepository events, AdmissionTokens tokens,
                       ApplicationEventPublisher publisher, Clock clock,
                       @Value("${festpass.queue.batch-size}") int batchSize,
                       @Value("${festpass.queue.admission-window}") Duration admissionWindow) {
        this.eventService = eventService;
        this.events = events;
        this.tokens = tokens;
        this.publisher = publisher;
        this.clock = clock;
        this.batchSize = batchSize;
        this.admissionWindow = admissionWindow;
    }

    /** Joins the queue, or returns your current place if you're already in it. */
    public QueueStatus join(Long eventId, Long userId) {
        Event event = requireFlashDrop(eventId);
        Instant now = Instant.now(clock);
        if (!now.isBefore(event.getSaleClosesAt())) {
            throw new ApiException(ErrorCode.SALE_NOT_OPEN, "Ticket sales have closed");
        }
        EventQueue queue = queues.computeIfAbsent(eventId, id -> new EventQueue());
        boolean joined = false;
        synchronized (queue) {
            Admission admission = queue.admissions.get(userId);
            boolean stillAdmitted = admission != null && now.isBefore(admission.expiresAt());
            if (!stillAdmitted && !queue.numberByUser.containsKey(userId)) {
                queue.admissions.remove(userId); // an expired admission: back of the queue
                long number = queue.nextNumber++;
                queue.waitingByNumber.put(number, userId);
                queue.numberByUser.put(userId, number);
                joined = true;
            }
        }
        if (joined) {
            publisher.publishEvent(new QueueChanged(eventId));
        }
        return status(event, userId, now);
    }

    public QueueStatus status(Long eventId, Long userId) {
        return status(requireFlashDrop(eventId), userId, Instant.now(clock));
    }

    public QueueSnapshot snapshot(Long eventId) {
        Instant opensAt = events.findById(eventId).map(Event::getSaleOpensAt).orElse(null);
        EventQueue queue = queues.get(eventId);
        if (queue == null) {
            return new QueueSnapshot(eventId, 0, 0, opensAt);
        }
        synchronized (queue) {
            return new QueueSnapshot(eventId, queue.waitingByNumber.size(), queue.admittedSoFar, opensAt);
        }
    }

    /** For the hold endpoint: flash drops only accept holds with a valid admission token. */
    public void requireAdmitted(Long eventId, Long userId, String admissionToken, Instant now) {
        if (!tokens.isValid(admissionToken, userId, eventId, now)) {
            throw new ApiException(ErrorCode.NOT_ADMITTED,
                    "This is a flash drop. Join the waiting room and wait to be let in.");
        }
    }

    /**
     * Lets in the next batch for every flash drop whose sale has opened. Called by the admission job
     * every few seconds (and directly by tests).
     *
     * @return how many people were admitted
     */
    public int admitDue() {
        Instant now = Instant.now(clock);
        int total = 0;
        for (Long eventId : List.copyOf(queues.keySet())) {
            Optional<Event> event = events.findById(eventId);
            if (event.isEmpty() || !now.isBefore(event.get().getSaleClosesAt())) {
                queues.remove(eventId); // sale over: nobody else will be let in
                continue;
            }
            if (now.isBefore(event.get().getSaleOpensAt())) {
                continue;
            }
            List<Admitted> admitted = admitBatch(queues.get(eventId), eventId, now);
            admitted.forEach(publisher::publishEvent);
            if (!admitted.isEmpty()) {
                publisher.publishEvent(new QueueChanged(eventId));
            }
            total += admitted.size();
        }
        return total;
    }

    private List<Admitted> admitBatch(EventQueue queue, Long eventId, Instant now) {
        List<Admitted> admitted = new ArrayList<>();
        if (queue == null) {
            return admitted;
        }
        Instant expiresAt = now.plus(admissionWindow);
        synchronized (queue) {
            while (admitted.size() < batchSize && !queue.waitingByNumber.isEmpty()) {
                var next = queue.waitingByNumber.pollFirstEntry();
                Long userId = next.getValue();
                queue.numberByUser.remove(userId);
                queue.nextToAdmit = next.getKey() + 1;
                queue.admittedSoFar++;
                String token = tokens.issue(userId, eventId, expiresAt);
                queue.admissions.put(userId, new Admission(token, expiresAt));
                admitted.add(new Admitted(eventId, userId, token, expiresAt));
            }
        }
        return admitted;
    }

    private QueueStatus status(Event event, Long userId, Instant now) {
        Long eventId = event.getId();
        EventQueue queue = queues.get(eventId);
        if (queue == null) {
            return new QueueStatus(eventId, Status.NOT_IN_QUEUE, null, 0, 0, event.getSaleOpensAt(), null, null);
        }
        synchronized (queue) {
            long waiting = queue.waitingByNumber.size();
            Admission admission = queue.admissions.get(userId);
            if (admission != null) {
                return now.isBefore(admission.expiresAt())
                        ? new QueueStatus(eventId, Status.ADMITTED, null, waiting, queue.admittedSoFar,
                                event.getSaleOpensAt(), admission.token(), admission.expiresAt())
                        : new QueueStatus(eventId, Status.EXPIRED, null, waiting, queue.admittedSoFar,
                                event.getSaleOpensAt(), null, admission.expiresAt());
            }
            Long number = queue.numberByUser.get(userId);
            if (number == null) {
                return new QueueStatus(eventId, Status.NOT_IN_QUEUE, null, waiting, queue.admittedSoFar,
                        event.getSaleOpensAt(), null, null);
            }
            // People only ever leave from the front, in number order, so everyone between the next
            // number to admit and this user is still ahead of them.
            long position = number - queue.nextToAdmit + 1;
            return new QueueStatus(eventId, Status.WAITING, position, waiting, queue.admittedSoFar,
                    event.getSaleOpensAt(), null, null);
        }
    }

    private Event requireFlashDrop(Long eventId) {
        Event event = eventService.requireBookable(eventId);
        if (!event.isFlashDrop()) {
            throw ApiException.invalidState("This event has no waiting room");
        }
        return event;
    }
}
