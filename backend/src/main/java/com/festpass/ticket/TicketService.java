package com.festpass.ticket;

import com.festpass.auth.AppUserPrincipal;
import com.festpass.booking.Order;
import com.festpass.booking.OrderRepository;
import com.festpass.common.ApiException;
import com.festpass.event.Event;
import com.festpass.event.EventRepository;
import com.festpass.event.TicketTier;
import com.festpass.event.TicketTierRepository;
import com.festpass.seating.Seat;
import com.festpass.seating.SeatRepository;
import com.festpass.ticket.TicketDtos.TicketView;
import com.festpass.user.User;
import com.festpass.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The ticket wallet and ticket downloads. Users only ever see tickets from their own orders. */
@Service
public class TicketService {

    private final TicketRepository tickets;
    private final OrderRepository orders;
    private final EventRepository events;
    private final TicketTierRepository tiers;
    private final SeatRepository seats;
    private final UserRepository users;
    private final TicketCodes codes;
    private final TicketPdfRenderer pdfRenderer;
    private final Clock clock;

    public TicketService(TicketRepository tickets, OrderRepository orders, EventRepository events,
                         TicketTierRepository tiers, SeatRepository seats, UserRepository users, TicketCodes codes,
                         TicketPdfRenderer pdfRenderer, Clock clock) {
        this.tickets = tickets;
        this.orders = orders;
        this.events = events;
        this.tiers = tiers;
        this.seats = seats;
        this.users = users;
        this.codes = codes;
        this.pdfRenderer = pdfRenderer;
        this.clock = clock;
    }

    /** Everything the confirmation email needs. */
    public record OrderDocuments(User buyer, Event event, List<TicketDetails> tickets, byte[] pdf, String ics) {}

    @Transactional(readOnly = true)
    public List<TicketView> myTickets(AppUserPrincipal user) {
        return details(tickets.findOwnedBy(user.id())).stream()
                .sorted(Comparator.comparing((TicketDetails d) -> d.event().getStartsAt())
                        .thenComparing(TicketDetails::seatLabel))
                .map(TicketView::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public TicketView get(UUID ticketId, AppUserPrincipal user) {
        return TicketView.of(owned(ticketId, user));
    }

    @Transactional(readOnly = true)
    public byte[] qrPng(UUID ticketId, AppUserPrincipal user) {
        return QrCodes.png(owned(ticketId, user).code(), 480);
    }

    @Transactional(readOnly = true)
    public byte[] pdf(UUID ticketId, AppUserPrincipal user) {
        return pdfRenderer.render(List.of(owned(ticketId, user)));
    }

    @Transactional(readOnly = true)
    public String calendar(UUID ticketId, AppUserPrincipal user) {
        TicketDetails d = owned(ticketId, user);
        return CalendarFiles.forEvent(d.event(), "Your FestPass ticket: " + d.tier().getName() + ", " + d.seatLabel(),
                Instant.now(clock));
    }

    @Transactional(readOnly = true)
    public OrderDocuments documentsForOrder(Long orderId) {
        Order order = orders.findById(orderId).orElseThrow();
        List<TicketDetails> orderTickets = details(tickets.findByOrderIdOrderByCreatedAtAscIdAsc(orderId));
        Event event = events.findById(order.getEventId()).orElseThrow();
        User buyer = users.findById(order.getUserId()).orElseThrow();
        String ics = CalendarFiles.forEvent(event,
                "Your FestPass tickets (" + orderTickets.size() + "). Show the QR code at the gate.", Instant.now(clock));
        return new OrderDocuments(buyer, event, orderTickets, pdfRenderer.render(orderTickets), ics);
    }

    private TicketDetails owned(UUID ticketId, AppUserPrincipal user) {
        Ticket ticket = tickets.findById(ticketId).orElseThrow(() -> ApiException.notFound("Ticket"));
        boolean mine = orders.findById(ticket.getOrderId()).map(o -> o.getUserId().equals(user.id())).orElse(false);
        if (!mine) {
            throw ApiException.notFound("Ticket");
        }
        return details(List.of(ticket)).get(0);
    }

    /** Loads events, tiers and seats for many tickets with one query each. */
    private List<TicketDetails> details(List<Ticket> list) {
        Map<Long, Event> eventById = byId(events.findAllById(ids(list, Ticket::getEventId)), Event::getId);
        Map<Long, TicketTier> tierById = byId(tiers.findAllById(ids(list, Ticket::getTierId)), TicketTier::getId);
        Map<Long, Seat> seatById = byId(seats.findAllById(ids(list, Ticket::getSeatId)), Seat::getId);
        return list.stream().map(t -> new TicketDetails(t, eventById.get(t.getEventId()), tierById.get(t.getTierId()),
                t.getSeatId() == null ? null : seatById.get(t.getSeatId()),
                codes.codeFor(t.getId(), t.getEventId()))).toList();
    }

    private static List<Long> ids(List<Ticket> list, Function<Ticket, Long> id) {
        return list.stream().map(id).filter(Objects::nonNull).distinct().toList();
    }

    private static <T> Map<Long, T> byId(List<T> items, Function<T, Long> id) {
        return items.stream().collect(Collectors.toMap(id, Function.identity()));
    }
}
