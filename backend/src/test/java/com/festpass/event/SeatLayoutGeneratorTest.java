package com.festpass.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.festpass.event.SeatLayoutGenerator.SeatPosition;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Plain unit tests: no Spring, no database. */
class SeatLayoutGeneratorTest {

    @Test
    void rowLabelsGoPastZ() {
        assertThat(RowLabels.of(0)).isEqualTo("A");
        assertThat(RowLabels.of(25)).isEqualTo("Z");
        assertThat(RowLabels.of(26)).isEqualTo("AA");
        assertThat(RowLabels.of(27)).isEqualTo("AB");
        assertThat(RowLabels.of(51)).isEqualTo("AZ");
        assertThat(RowLabels.of(52)).isEqualTo("BA");
        assertThatThrownBy(() -> RowLabels.of(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void layoutHasOneSeatPerRowAndNumber() {
        List<SeatPosition> seats = SeatLayoutGenerator.layout(4, 6, List.of(), 0);
        assertThat(seats).hasSize(24);
        assertThat(seats.stream().map(s -> s.rowLabel() + s.seatNumber()).distinct()).hasSize(24);
    }

    @Test
    void sectionIsCentredAndRowsAreEvenlySpaced() {
        List<SeatPosition> seats = SeatLayoutGenerator.layout(2, 5, List.of(), 100);
        assertThat(seats.get(0).x()).isEqualTo(-seats.get(4).x());
        assertThat(seats.get(0).y()).isEqualTo(100);
        assertThat(seats.get(5).y()).isEqualTo(100 + SeatLayoutGenerator.ROW_PITCH);
    }

    @Test
    void aislesAddExtraSpace() {
        List<SeatPosition> row = SeatLayoutGenerator.layout(1, 8, List.of(2, 6), 0);
        int pitch = SeatLayoutGenerator.SEAT_PITCH;
        int aisle = SeatLayoutGenerator.AISLE_WIDTH;
        assertThat(row.get(1).x() - row.get(0).x()).isEqualTo(pitch);
        assertThat(row.get(2).x() - row.get(1).x()).isEqualTo(pitch + aisle); // after seat 2
        assertThat(row.get(6).x() - row.get(5).x()).isEqualTo(pitch + aisle); // after seat 6
        assertThat(row.get(7).x() - row.get(0).x()).isEqualTo(7 * pitch + 2 * aisle);
    }

    @Test
    void nextSectionStartsBelowTheLastRow() {
        assertThat(SeatLayoutGenerator.nextSectionY(null)).isZero();
        assertThat(SeatLayoutGenerator.nextSectionY(200)).isEqualTo(200 + SeatLayoutGenerator.SECTION_GAP);
    }

    @Test
    void slugsAreUrlSafe() {
        assertThat(EventService.slugify("Rhythm Night: Saturday Showcase")).isEqualTo("rhythm-night-saturday-showcase");
        assertThat(EventService.slugify("  Café   Ñights!! ")).isEqualTo("cafe-nights");
        assertThat(EventService.slugify("!!!")).isEqualTo("event");
    }
}
