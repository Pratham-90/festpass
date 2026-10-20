package com.festpass.ticket;

import com.festpass.booking.Hold;
import com.festpass.booking.Order;
import com.festpass.seating.SeatRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Creates the tickets for a hold that has just been paid for: one per seat, or {@code quantity} for GA. */
@Component
public class TicketIssuer {

    private final TicketRepository tickets;
    private final SeatRepository seats;

    public TicketIssuer(TicketRepository tickets, SeatRepository seats) {
        this.tickets = tickets;
        this.seats = seats;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<Ticket> issue(Order order, Hold hold, String holderName, Instant now) {
        List<Ticket> issued = new ArrayList<>();
        if (hold.isGeneralAdmission()) {
            for (int i = 0; i < hold.getQuantity(); i++) {
                issued.add(new Ticket(UUID.randomUUID(), order.getId(), order.getEventId(), hold.getTierId(), null,
                        holderName, now));
            }
        } else {
            seats.findByHoldIdOrderById(hold.getId()).forEach(seat -> issued.add(new Ticket(UUID.randomUUID(),
                    order.getId(), order.getEventId(), seat.getTierId(), seat.getId(), holderName, now)));
        }
        // Flush now: the unique index on tickets.seat_id is checked here, inside the payment transaction.
        return tickets.saveAllAndFlush(issued);
    }
}
