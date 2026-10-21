package com.festpass.realtime;

import com.festpass.booking.HoldEnded;
import com.festpass.checkin.CheckinRecorded;
import com.festpass.event.AvailabilityChanged;
import com.festpass.queue.QueueEvents.Admitted;
import com.festpass.queue.QueueEvents.QueueChanged;
import com.festpass.realtime.RealtimeMessages.AdmissionMessage;
import com.festpass.realtime.RealtimeMessages.CheckinUpdate;
import com.festpass.realtime.RealtimeMessages.HoldUpdate;
import com.festpass.realtime.RealtimeMessages.LastScan;
import com.festpass.seating.SeatsChanged;
import com.festpass.ticket.TicketRepository;
import com.festpass.user.UserRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns domain events into WebSocket messages. Booking and check-in events are handled only
 * after their transaction commits (TransactionalEventListener's default phase), so a browser
 * never sees a change that was rolled back.
 */
@Component
public class RealtimeEventListener {

    private final RealtimePublisher publisher;
    private final UserRepository users;
    private final TicketRepository tickets;

    public RealtimeEventListener(RealtimePublisher publisher, UserRepository users, TicketRepository tickets) {
        this.publisher = publisher;
        this.users = users;
        this.tickets = tickets;
    }

    @TransactionalEventListener
    public void onSeatsChanged(SeatsChanged event) {
        publisher.seatsChanged(event.eventId(), event.seatIds(), event.status());
    }

    @TransactionalEventListener
    public void onAvailabilityChanged(AvailabilityChanged event) {
        publisher.statsChanged(event.eventId());
    }

    @TransactionalEventListener
    public void onHoldEnded(HoldEnded event) {
        users.findById(event.userId()).ifPresent(user -> publisher.toUser(user.getEmail(), RealtimeMessages.USER_HOLDS,
                new HoldUpdate(event.holdId(), event.eventId(), event.status())));
    }

    @TransactionalEventListener
    public void onCheckin(CheckinRecorded event) {
        publisher.toTopic(RealtimeMessages.checkins(event.eventId()), new CheckinUpdate(event.eventId(),
                tickets.countByEventIdAndCheckedInAtIsNotNull(event.eventId()),
                tickets.countByEventId(event.eventId()),
                new LastScan(event.result(), event.tierName(), event.seatLabel(), event.at())));
    }

    /** The waiting room isn't transactional, so these arrive straight away. */
    @EventListener
    public void onQueueChanged(QueueChanged event) {
        publisher.queueChanged(event.eventId());
    }

    @EventListener
    public void onAdmitted(Admitted event) {
        users.findById(event.userId()).ifPresent(user -> publisher.toUser(user.getEmail(), RealtimeMessages.USER_ADMISSION,
                new AdmissionMessage(event.eventId(), event.admissionToken(), event.expiresAt())));
    }
}
