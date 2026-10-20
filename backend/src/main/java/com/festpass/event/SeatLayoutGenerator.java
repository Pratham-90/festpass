package com.festpass.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Works out where each seat of a new section goes on the seat map.
 * Coordinates are seat centres in abstract SVG units. Each section is centred on x = 0,
 * so sections of different widths line up like an auditorium; the frontend fits the
 * whole map to the screen using the bounds returned with the seat map.
 */
public final class SeatLayoutGenerator {

    public static final int SEAT_PITCH = 28;   // distance between neighbouring seats
    public static final int ROW_PITCH = 32;    // distance between rows
    public static final int AISLE_WIDTH = 28;  // extra gap for each aisle
    public static final int SECTION_GAP = 64;  // gap above a section (room for its label)

    private SeatLayoutGenerator() {}

    public record SeatPosition(String rowLabel, int seatNumber, int x, int y) {}

    /**
     * @param aisleAfter seat numbers followed by an aisle, e.g. [4, 12] gives 4 | 8 | rest
     * @param firstRowY  y of the first row (below any existing sections)
     */
    public static List<SeatPosition> layout(int rows, int seatsPerRow, List<Integer> aisleAfter, int firstRowY) {
        Set<Integer> aisles = new TreeSet<>(aisleAfter == null ? List.of() : aisleAfter);
        int width = (seatsPerRow - 1) * SEAT_PITCH + aisles.size() * AISLE_WIDTH;
        int left = -width / 2;

        List<SeatPosition> seats = new ArrayList<>(rows * seatsPerRow);
        for (int r = 0; r < rows; r++) {
            String row = RowLabels.of(r);
            int y = firstRowY + r * ROW_PITCH;
            int aislesPassed = 0;
            for (int n = 1; n <= seatsPerRow; n++) {
                if (aisles.contains(n - 1)) {
                    aislesPassed++;
                }
                int x = left + (n - 1) * SEAT_PITCH + aislesPassed * AISLE_WIDTH;
                seats.add(new SeatPosition(row, n, x, y));
            }
        }
        return seats;
    }

    /** Where the first row of a new section goes, given the lowest existing row (null if none). */
    public static int nextSectionY(Integer maxExistingY) {
        return maxExistingY == null ? 0 : maxExistingY + SECTION_GAP;
    }
}
