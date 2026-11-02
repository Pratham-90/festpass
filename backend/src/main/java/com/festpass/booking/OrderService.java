package com.festpass.booking;

import com.festpass.auth.AppUserPrincipal;
import com.festpass.booking.BookingDtos.OrderItem;
import com.festpass.booking.BookingDtos.OrderView;
import com.festpass.common.ApiException;
import com.festpass.common.ErrorCode;
import com.festpass.event.EventRepository;
import com.festpass.event.TicketTier;
import com.festpass.event.TicketTierRepository;
import com.festpass.seating.SeatRepository;
import com.festpass.ticket.Ticket;
import com.festpass.ticket.TicketRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orders (PLAN.md 5.4). Creating an order is idempotent: the same Idempotency-Key, or a second
 * request for the same hold, returns the existing order instead of making a new one.
 */
@Service
public class OrderService {

    private final OrderRepository orders;
    private final HoldRepository holds;
    private final SeatRepository seats;
    private final TicketTierRepository tiers;
    private final TicketRepository tickets;
    private final EventRepository events;
    private final Clock clock;

    public OrderService(OrderRepository orders, HoldRepository holds, SeatRepository seats, TicketTierRepository tiers,
                        TicketRepository tickets, EventRepository events, Clock clock) {
        this.orders = orders;
        this.holds = holds;
        this.seats = seats;
        this.tiers = tiers;
        this.tickets = tickets;
        this.events = events;
        this.clock = clock;
    }

    public record CreatedOrder(OrderView order, boolean created) {}

    @Transactional
    public CreatedOrder create(UUID holdId, String idempotencyKey, AppUserPrincipal user) {
        var byKey = orders.findByIdempotencyKey(idempotencyKey);
        if (byKey.isPresent()) {
            Order existing = byKey.get();
            if (!existing.getUserId().equals(user.id()) || !existing.getHoldId().equals(holdId)) {
                throw new ApiException(ErrorCode.CONFLICT, "This Idempotency-Key was already used for a different order");
            }
            return new CreatedOrder(view(existing), false);
        }

        // Locking the hold makes concurrent requests for the same hold queue up here.
        Hold hold = holds.findByIdForUpdate(holdId)
                .filter(h -> h.getUserId().equals(user.id()))
                .orElseThrow(() -> ApiException.notFound("Hold"));
        var byHold = orders.findByHoldId(holdId);
        if (byHold.isPresent()) {
            return new CreatedOrder(view(byHold.get()), false);
        }
        if (hold.getStatus() != Hold.Status.ACTIVE || !hold.getExpiresAt().isAfter(Instant.now(clock))) {
            throw new ApiException(ErrorCode.HOLD_EXPIRED, "Your hold has expired. Pick your tickets again.");
        }

        long amount = hold.isGeneralAdmission()
                ? tiers.findById(hold.getTierId()).orElseThrow().getPricePaise() * hold.getQuantity()
                : seats.totalPricePaise(holdId);
        Order order = orders.save(new Order(user.id(), hold.getEventId(), holdId, idempotencyKey, amount, Instant.now(clock)));
        return new CreatedOrder(view(order), true);
    }

    @Transactional(readOnly = true)
    public OrderView get(Long orderId, AppUserPrincipal user) {
        return view(requireOwned(orderId, user));
    }

    @Transactional(readOnly = true)
    public Order requireOwned(Long orderId, AppUserPrincipal user) {
        return orders.findById(orderId)
                .filter(o -> o.getUserId().equals(user.id()))
                .orElseThrow(() -> ApiException.notFound("Order"));
    }

    @Transactional(readOnly = true)
    public OrderView view(Order order) {
        Hold hold = holds.findById(order.getHoldId()).orElseThrow();
        Map<Long, TicketTier> tierById = tiers.findByEventIdOrderByIdAsc(order.getEventId()).stream()
                .collect(Collectors.toMap(TicketTier::getId, Function.identity()));
        var event = events.findById(order.getEventId());
        String eventSlug = event.map(e -> e.getSlug()).orElse(null);
        String eventTitle = event.map(e -> e.getTitle()).orElse(null);

        List<OrderItem> items;
        if (hold.isGeneralAdmission()) {
            TicketTier tier = tierById.get(hold.getTierId());
            items = List.of(new OrderItem(tier.getId(), tier.getName(), tier.getPricePaise(), hold.getQuantity(), null));
        } else {
            // Seats of an expired or cancelled hold have been given back, so failed orders may list none.
            items = seats.findByHoldIdOrderById(hold.getId()).stream().map(s -> {
                TicketTier tier = tierById.get(s.getTierId());
                return new OrderItem(tier.getId(), tier.getName(), tier.getPricePaise(), 1, HoldService.seatRef(s));
            }).toList();
        }
        List<UUID> ticketIds = tickets.findByOrderIdOrderByCreatedAtAscIdAsc(order.getId()).stream()
                .map(Ticket::getId).toList();
        return new OrderView(order.getId(), order.getEventId(), eventSlug, eventTitle, order.getHoldId(), order.getStatus(),
                order.getAmountPaise(), order.getCreatedAt(), order.getPaidAt(), hold.getExpiresAt(), items, ticketIds);
    }
}
