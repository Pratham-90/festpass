package com.festpass.seating;

import com.festpass.auth.AppUserPrincipal;
import com.festpass.event.Event;
import com.festpass.event.EventDtos.SectionView;
import com.festpass.event.EventDtos.TierView;
import com.festpass.event.EventService;
import com.festpass.seating.SeatMapView.Bounds;
import com.festpass.seating.SeatMapView.SeatView;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeatMapService {

    private final EventService eventService;
    private final SeatRepository seats;
    private final Clock clock;

    public SeatMapService(EventService eventService, SeatRepository seats, Clock clock) {
        this.eventService = eventService;
        this.seats = seats;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SeatMapView forPublicEvent(Long eventId) {
        return build(eventService.requirePublic(eventId));
    }

    /** Organizers can see the map of their own drafts while designing them. */
    @Transactional(readOnly = true)
    public SeatMapView forOrganizer(Long eventId, AppUserPrincipal actor) {
        return build(eventService.requireManageable(eventId, actor));
    }

    private SeatMapView build(Event event) {
        Instant now = Instant.now(clock);
        List<TierView> tiers = eventService.tierViews(event);
        Map<Long, String> colorByTier = tiers.stream().collect(Collectors.toMap(TierView::id, TierView::colorHex));
        List<Seat> eventSeats = seats.findForMap(event.getId());

        List<SeatView> seatViews = eventSeats.stream()
                .map(s -> new SeatView(s.getId(), s.getSection(), s.getRowLabel(), s.getSeatNumber(), s.getX(), s.getY(),
                        s.effectiveStatus(now), s.getTierId(), colorByTier.get(s.getTierId())))
                .toList();

        // Sections in the order they appear on the map (seats are sorted top to bottom).
        Map<String, List<Seat>> bySection = eventSeats.stream()
                .collect(Collectors.groupingBy(Seat::getSection, LinkedHashMap::new, Collectors.toList()));
        List<SectionView> sections = bySection.entrySet().stream().map(entry -> {
            List<Seat> s = entry.getValue();
            return new SectionView(entry.getKey(), s.get(0).getTierId(), s.size(),
                    min(s, Seat::getX), min(s, Seat::getY), max(s, Seat::getX), max(s, Seat::getY));
        }).toList();

        Bounds bounds = eventSeats.isEmpty() ? null : new Bounds(
                min(eventSeats, Seat::getX), min(eventSeats, Seat::getY),
                max(eventSeats, Seat::getX), max(eventSeats, Seat::getY));

        return new SeatMapView(event.getId(), event.getSeatingMode(), tiers, sections, seatViews, bounds);
    }

    private static int min(List<Seat> seats, Function<Seat, Integer> coordinate) {
        return seats.stream().mapToInt(coordinate::apply).min().orElse(0);
    }

    private static int max(List<Seat> seats, Function<Seat, Integer> coordinate) {
        return seats.stream().mapToInt(coordinate::apply).max().orElse(0);
    }
}
