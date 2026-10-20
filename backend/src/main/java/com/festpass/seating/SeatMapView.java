package com.festpass.seating;

import com.festpass.event.EventDtos.SectionView;
import com.festpass.event.EventDtos.TierView;
import com.festpass.event.EventEnums.SeatingMode;
import java.util.List;

/**
 * Everything the frontend needs to draw the seat map. For GENERAL events, sections and seats
 * are empty and the tiers carry the remaining counts. {@code bounds} is null when there are no seats.
 */
public record SeatMapView(
        Long eventId,
        SeatingMode seatingMode,
        List<TierView> tiers,
        List<SectionView> sections,
        List<SeatView> seats,
        Bounds bounds) {

    public record SeatView(Long id, String section, String row, int number, int x, int y,
                           SeatStatus status, Long tierId, String color) {}

    public record Bounds(int minX, int minY, int maxX, int maxY) {}
}
