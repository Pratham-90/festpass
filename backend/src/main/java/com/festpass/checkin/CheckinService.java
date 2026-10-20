package com.festpass.checkin;

import com.festpass.auth.AppUserPrincipal;
import com.festpass.checkin.CheckinScan.Result;
import com.festpass.common.ApiException;
import com.festpass.event.Event;
import com.festpass.event.EventRepository;
import com.festpass.event.GateAssignmentRepository;
import com.festpass.event.TicketTierRepository;
import com.festpass.seating.SeatRepository;
import com.festpass.ticket.Ticket;
import com.festpass.ticket.TicketCodes;
import com.festpass.ticket.TicketRepository;
import com.festpass.user.Role;
import com.festpass.user.User;
import com.festpass.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gate scanning (PLAN.md 5.6). Check-in is one conditional update, so a ticket can only ever be
 * let in once, even when two gates scan it at the same moment. Every scan is logged.
 */
@Service
public class CheckinService {

    private final TicketCodes codes;
    private final TicketRepository tickets;
    private final CheckinScanRepository scans;
    private final EventRepository events;
    private final GateAssignmentRepository gateAssignments;
    private final TicketTierRepository tiers;
    private final SeatRepository seats;
    private final UserRepository users;
    private final Clock clock;

    public CheckinService(TicketCodes codes, TicketRepository tickets, CheckinScanRepository scans,
                          EventRepository events, GateAssignmentRepository gateAssignments, TicketTierRepository tiers,
                          SeatRepository seats, UserRepository users, Clock clock) {
        this.codes = codes;
        this.tickets = tickets;
        this.scans = scans;
        this.events = events;
        this.gateAssignments = gateAssignments;
        this.tiers = tiers;
        this.seats = seats;
        this.users = users;
        this.clock = clock;
    }

    public record ScanResponse(
            Result result, String message, UUID ticketId, String holderName, String tierName, String seatLabel,
            Instant checkedInAt, String checkedInBy) {}

    public record LastScan(Result result, Instant scannedAt) {}

    public record CheckinStats(Long eventId, long checkedIn, long total, LastScan lastScan) {}

    @Transactional
    public ScanResponse scan(Long eventId, String code, AppUserPrincipal staff) {
        Event event = events.findById(eventId).orElseThrow(() -> ApiException.notFound("Event"));
        requireCanScan(event, staff);
        Instant now = Instant.now(clock);

        Optional<TicketCodes.Parsed> parsed = codes.verify(code);
        if (parsed.isEmpty()) {
            return log(eventId, null, staff, now,
                    new ScanResponse(Result.INVALID, "Not a valid FestPass ticket", null, null, null, null, null, null));
        }
        UUID ticketId = parsed.get().ticketId();
        Optional<Ticket> ticket = tickets.findById(ticketId);
        if (ticket.isEmpty()) {
            return log(eventId, null, staff, now,
                    new ScanResponse(Result.INVALID, "Ticket not found", null, null, null, null, null, null));
        }
        if (parsed.get().eventId() != eventId) {
            String other = events.findById(parsed.get().eventId()).map(Event::getTitle).orElse("another event");
            return log(eventId, ticketId, staff, now, response(Result.WRONG_EVENT,
                    "This ticket is for " + other, ticket.get(), null, null));
        }

        if (tickets.checkIn(ticketId, eventId, now, staff.id()) == 1) {
            return log(eventId, ticketId, staff, now, response(Result.VALID, "Welcome in!", ticket.get(), now, staff.name()));
        }
        // Someone scanned it first. checkIn() cleared the persistence context, so this reads the current row.
        Ticket used = tickets.findById(ticketId).orElseThrow();
        String by = users.findById(used.getCheckedInBy()).map(User::getName).orElse("another gate");
        return log(eventId, ticketId, staff, now, response(Result.ALREADY_USED, "Already scanned", used,
                used.getCheckedInAt(), by));
    }

    @Transactional(readOnly = true)
    public CheckinStats stats(Long eventId, AppUserPrincipal viewer) {
        Event event = events.findById(eventId).orElseThrow(() -> ApiException.notFound("Event"));
        boolean organizer = event.getOrganizerId().equals(viewer.id());
        if (!organizer) {
            requireCanScan(event, viewer);
        }
        LastScan last = scans.findFirstByEventIdOrderByScannedAtDescIdDesc(eventId)
                .map(s -> new LastScan(s.getResult(), s.getScannedAt())).orElse(null);
        return new CheckinStats(eventId, tickets.countByEventIdAndCheckedInAtIsNotNull(eventId),
                tickets.countByEventId(eventId), last);
    }

    /** Admins can scan anywhere; gate staff only at events they are assigned to. */
    private void requireCanScan(Event event, AppUserPrincipal staff) {
        boolean allowed = staff.isAdmin()
                || (staff.role() == Role.GATE_STAFF && gateAssignments.existsByUserIdAndEventId(staff.id(), event.getId()));
        if (!allowed) {
            throw ApiException.forbidden("You are not on the gate team for this event");
        }
    }

    private ScanResponse response(Result result, String message, Ticket ticket, Instant checkedInAt, String by) {
        String tierName = tiers.findById(ticket.getTierId()).map(t -> t.getName()).orElse(null);
        String seatLabel = ticket.getSeatId() == null ? "General admission" : seats.findById(ticket.getSeatId())
                .map(s -> s.getSection() + " · Row " + s.getRowLabel() + " · Seat " + s.getSeatNumber())
                .orElse(null);
        return new ScanResponse(result, message, ticket.getId(), ticket.getHolderName(), tierName, seatLabel,
                checkedInAt, by);
    }

    private ScanResponse log(Long eventId, UUID ticketId, AppUserPrincipal staff, Instant now, ScanResponse response) {
        scans.save(new CheckinScan(eventId, ticketId, staff.id(), response.result(), now));
        return response;
    }
}
