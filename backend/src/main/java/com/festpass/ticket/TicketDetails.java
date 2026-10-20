package com.festpass.ticket;

import com.festpass.event.Event;
import com.festpass.event.TicketTier;
import com.festpass.seating.Seat;

/** A ticket with everything needed to show it, print it or email it. {@code seat} is null for general admission. */
public record TicketDetails(Ticket ticket, Event event, TicketTier tier, Seat seat, String code) {

    public String seatLabel() {
        return seat == null
                ? "General admission"
                : seat.getSection() + " · Row " + seat.getRowLabel() + " · Seat " + seat.getSeatNumber();
    }
}
