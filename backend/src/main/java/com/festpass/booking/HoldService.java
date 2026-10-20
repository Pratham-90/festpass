package com.festpass.booking;

import com.festpass.auth.AppUserPrincipal;
import com.festpass.booking.BookingDtos.HoldRequest;
import com.festpass.booking.BookingDtos.HoldView;
import com.festpass.booking.BookingDtos.SeatRef;
import com.festpass.common.ApiException;
import com.festpass.common.ErrorCode;
import com.festpass.event.Event;
import com.festpass.event.EventEnums.SeatingMode;
import com.festpass.event.EventService;
import com.festpass.event.TicketTier;
import com.festpass.event.TicketTierRepository;
import com.festpass.seating.Seat;
import com.festpass.seating.SeatRepository;
import com.festpass.ticket.TicketRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Holding seats or GA tickets (PLAN.md 5.1 to 5.3). A hold is one transaction and all-or-nothing:
 * if any requested seat is gone, the whole request rolls back.
 */
@Service
public class HoldService {

    private final EventService eventService;
    private final HoldRepository holds;
    private final SeatRepository seats;
    private final TicketTierRepository tiers;
    private final TicketRepository tickets;
    private final OrderRepository orders;
    private final BookingLocks locks;
    private final HoldReleaser releaser;
    private final Clock clock;

    public HoldService(EventService eventService, HoldRepository holds, SeatRepository seats,
                       TicketTierRepository tiers, TicketRepository tickets, OrderRepository orders,
                       BookingLocks locks, HoldReleaser releaser, Clock clock) {
        this.eventService = eventService;
        this.holds = holds;
        this.seats = seats;
        this.tiers = tiers;
        this.tickets = tickets;
        this.orders = orders;
        this.locks = locks;
        this.releaser = releaser;
        this.clock = clock;
    }

    @Transactional
    public HoldView hold(Long eventId, HoldRequest request, AppUserPrincipal user) {
        Instant now = Instant.now(clock);
        Event event = eventService.requireBookable(eventId);
        if (now.isBefore(event.getSaleOpensAt()) || !now.isBefore(event.getSaleClosesAt())) {
            throw new ApiException(ErrorCode.SALE_NOT_OPEN,
                    now.isBefore(event.getSaleOpensAt()) ? "Tickets aren't on sale yet" : "Ticket sales have closed",
                    Map.of("saleOpensAt", event.getSaleOpensAt(), "saleClosesAt", event.getSaleClosesAt()));
        }

        boolean seated = event.getSeatingMode() == SeatingMode.SEATED;
        Set<Long> seatIds = seated ? requireSeatIds(request) : Set.of();
        TicketTier gaTier = seated ? null : requireGaTier(request, eventId);
        int quantity = seated ? seatIds.size() : request.quantity();

        // Per-user limit: serialise this user's requests for this event, then count.
        locks.lockUserForEvent(user.id(), eventId);
        int alreadyHeldOrOwned = holds.countActiveTickets(user.id(), eventId, now) + tickets.countOwned(user.id(), eventId);
        if (alreadyHeldOrOwned + quantity > event.getMaxTicketsPerUser()) {
            throw new ApiException(ErrorCode.LIMIT_EXCEEDED,
                    "You can have at most " + event.getMaxTicketsPerUser() + " tickets for this event",
                    Map.of("limit", event.getMaxTicketsPerUser(), "alreadyHeldOrOwned", alreadyHeldOrOwned));
        }

        UUID holdId = UUID.randomUUID();
        Instant expiresAt = now.plus(Duration.ofMinutes(event.getHoldMinutes()));
        holds.saveAndFlush(new Hold(holdId, eventId, user.id(), seated ? null : gaTier.getId(), quantity, expiresAt, now));

        if (seated) {
            int held = seats.hold(holdId, expiresAt, eventId, seatIds, now);
            if (held != seatIds.size()) {
                Set<Long> unavailable = new LinkedHashSet<>(seatIds);
                seats.findByHoldIdOrderById(holdId).forEach(s -> unavailable.remove(s.getId()));
                // Throwing rolls back the seats this request did get: all or nothing.
                throw new ApiException(ErrorCode.SEAT_UNAVAILABLE, "Some of those seats were just taken",
                        Map.of("unavailableSeatIds", List.copyOf(unavailable)));
            }
        } else if (tiers.reserve(gaTier.getId(), eventId, quantity) == 0) {
            throw new ApiException(ErrorCode.SOLD_OUT, "Not enough tickets left in " + gaTier.getName(),
                    Map.of("tierId", gaTier.getId(), "remaining", Math.max(0, gaTier.getCapacity() - gaTier.getReserved())));
        }

        return view(holdId, eventId, Hold.Status.ACTIVE, expiresAt, quantity, seated ? null : gaTier.getId());
    }

    /** Cancels the user's hold and gives the seats back straight away. Cancelling twice is harmless. */
    @Transactional
    public void release(UUID holdId, AppUserPrincipal user) {
        Hold hold = holds.findById(holdId)
                .filter(h -> h.getUserId().equals(user.id()))
                .orElseThrow(() -> ApiException.notFound("Hold"));
        // Lock the order first (same order as payments: order, then hold) so the two never deadlock.
        var order = orders.findByHoldId(holdId).flatMap(o -> orders.findByIdForUpdate(o.getId()));
        if (releaser.end(hold, Hold.Status.RELEASED)) {
            order.filter(o -> o.getStatus() == Order.Status.PENDING).ifPresent(Order::markFailed);
        }
    }

    private HoldView view(UUID holdId, Long eventId, Hold.Status status, Instant expiresAt, int quantity, Long tierId) {
        List<SeatRef> heldSeats = seats.findByHoldIdOrderById(holdId).stream().map(HoldService::seatRef).toList();
        return new HoldView(holdId, eventId, status, expiresAt, quantity, tierId, heldSeats);
    }

    static SeatRef seatRef(Seat s) {
        return new SeatRef(s.getId(), s.getSection(), s.getRowLabel(), s.getSeatNumber(), s.getTierId());
    }

    private static Set<Long> requireSeatIds(HoldRequest request) {
        if (request.seatIds() == null || request.seatIds().isEmpty()) {
            throw ApiException.validation("seatIds", "pick at least one seat");
        }
        if (request.tierId() != null || request.quantity() != null) {
            throw ApiException.validation("seatIds", "seated events take seatIds only, not tierId or quantity");
        }
        Set<Long> ids = new LinkedHashSet<>(request.seatIds());
        if (ids.size() != request.seatIds().size()) {
            throw ApiException.validation("seatIds", "must not repeat a seat");
        }
        return ids;
    }

    private TicketTier requireGaTier(HoldRequest request, Long eventId) {
        if (request.seatIds() != null && !request.seatIds().isEmpty()) {
            throw ApiException.validation("seatIds", "this event has no reserved seats; send tierId and quantity");
        }
        if (request.tierId() == null) {
            throw ApiException.validation("tierId", "is required");
        }
        if (request.quantity() == null) {
            throw ApiException.validation("quantity", "is required");
        }
        return tiers.findById(request.tierId())
                .filter(t -> t.getEventId().equals(eventId))
                .orElseThrow(() -> ApiException.validation("tierId", "is not a tier of this event"));
    }
}
