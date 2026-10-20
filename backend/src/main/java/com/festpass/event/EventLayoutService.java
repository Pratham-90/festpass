package com.festpass.event;

import com.festpass.auth.AppUserPrincipal;
import com.festpass.common.ApiException;
import com.festpass.event.EventDtos.SectionRequest;
import com.festpass.event.EventDtos.SectionView;
import com.festpass.event.EventDtos.TierRequest;
import com.festpass.event.EventDtos.TierView;
import com.festpass.event.EventEnums.EventStatus;
import com.festpass.event.EventEnums.SeatingMode;
import com.festpass.event.SeatLayoutGenerator.SeatPosition;
import com.festpass.seating.Seat;
import com.festpass.seating.SeatRepository;
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ticket tiers and seat sections of an event. Layout changes are only allowed while the
 * event is a draft, and lock the event row so two changes to one event never interleave.
 */
@Service
public class EventLayoutService {

    /** Default tier colours (first tier = event accent colour). */
    static final List<String> PALETTE = List.of("#ff3d81", "#7c5cff", "#22d3ee", "#f59e0b", "#22e39a", "#f43f5e");

    private final EventRepository events;
    private final TicketTierRepository tiers;
    private final SeatRepository seats;

    public EventLayoutService(EventRepository events, TicketTierRepository tiers, SeatRepository seats) {
        this.events = events;
        this.tiers = tiers;
        this.seats = seats;
    }

    @Transactional
    public TierView addTier(Long eventId, TierRequest request, AppUserPrincipal actor) {
        Event event = lockDraft(eventId, actor);
        String name = request.name().trim();
        if (tiers.existsByEventIdAndNameIgnoreCase(eventId, name)) {
            throw ApiException.validation("name", "This event already has a tier with this name");
        }
        int capacity;
        if (event.getSeatingMode() == SeatingMode.GENERAL) {
            if (request.capacity() == null) {
                throw ApiException.validation("capacity", "is required for general admission");
            }
            capacity = request.capacity();
        } else {
            if (request.capacity() != null) {
                throw ApiException.validation("capacity", "must be left out for seated events; seats decide it");
            }
            capacity = 0;
        }
        String color = request.colorHex() != null
                ? request.colorHex().toLowerCase()
                : PALETTE.get((int) (tiers.countByEventId(eventId) % PALETTE.size()));
        TicketTier tier = tiers.save(new TicketTier(eventId, name, request.pricePaise(), capacity, color));
        return new TierView(tier.getId(), tier.getName(), tier.getPricePaise(), capacity, capacity, tier.getColorHex());
    }

    /** Generates rows x seatsPerRow seats with aisle gaps, placed below any existing sections. */
    @Transactional
    public SectionView addSection(Long eventId, SectionRequest request, AppUserPrincipal actor) {
        Event event = lockDraft(eventId, actor);
        if (event.getSeatingMode() != SeatingMode.SEATED) {
            throw ApiException.invalidState("Sections are only for seated events");
        }
        String name = request.name().trim();
        TicketTier tier = tiers.findById(request.tierId())
                .filter(t -> t.getEventId().equals(eventId))
                .orElseThrow(() -> ApiException.validation("tierId", "is not a tier of this event"));
        if (seats.existsByEventIdAndSectionIgnoreCase(eventId, name)) {
            throw ApiException.validation("name", "This event already has a section with this name");
        }
        List<Integer> aisles = request.aisleAfter() == null ? List.of() : request.aisleAfter();
        for (int seat : aisles) {
            if (seat < 1 || seat >= request.seatsPerRow()) {
                throw ApiException.validation("aisleAfter",
                        "aisles must be between seat 1 and seat " + (request.seatsPerRow() - 1));
            }
        }
        if (new HashSet<>(aisles).size() != aisles.size()) {
            throw ApiException.validation("aisleAfter", "must not repeat a seat number");
        }

        int firstRowY = SeatLayoutGenerator.nextSectionY(seats.findMaxY(eventId));
        List<SeatPosition> positions =
                SeatLayoutGenerator.layout(request.rows(), request.seatsPerRow(), aisles, firstRowY);
        List<Seat> created = seats.saveAll(positions.stream()
                .map(p -> new Seat(eventId, tier.getId(), name, p.rowLabel(), p.seatNumber(), p.x(), p.y()))
                .toList());
        tiers.addCapacity(tier.getId(), created.size());

        return new SectionView(name, tier.getId(), created.size(),
                positions.stream().mapToInt(SeatPosition::x).min().orElse(0),
                positions.stream().mapToInt(SeatPosition::y).min().orElse(0),
                positions.stream().mapToInt(SeatPosition::x).max().orElse(0),
                positions.stream().mapToInt(SeatPosition::y).max().orElse(0));
    }

    private Event lockDraft(Long eventId, AppUserPrincipal actor) {
        Event event = events.findByIdForUpdate(eventId)
                .filter(e -> !e.isSandbox())
                .orElseThrow(() -> ApiException.notFound("Event"));
        EventService.checkCanManage(event, actor);
        if (event.getStatus() != EventStatus.DRAFT) {
            throw ApiException.invalidState("Tiers and sections can only be changed while the event is a draft");
        }
        return event;
    }
}
