package com.festpass.seating;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One seat. Its status only changes through the conditional SQL updates in the booking engine,
 * never by loading the entity and calling a setter.
 * A HELD seat whose hold has expired counts as available (see {@link #effectiveStatus(Instant)}).
 */
@Entity
@Table(name = "seats")
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "tier_id")
    private Long tierId;

    private String section;

    @Column(name = "row_label")
    private String rowLabel;

    @Column(name = "seat_number")
    private int seatNumber;

    private int x;

    private int y;

    @Enumerated(EnumType.STRING)
    private SeatStatus status;

    @Column(name = "hold_id")
    private UUID holdId;

    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;

    protected Seat() {}

    public Seat(Long eventId, Long tierId, String section, String rowLabel, int seatNumber, int x, int y) {
        this.eventId = eventId;
        this.tierId = tierId;
        this.section = section;
        this.rowLabel = rowLabel;
        this.seatNumber = seatNumber;
        this.x = x;
        this.y = y;
        this.status = SeatStatus.AVAILABLE;
    }

    public SeatStatus effectiveStatus(Instant now) {
        boolean expiredHold = status == SeatStatus.HELD && holdExpiresAt != null && holdExpiresAt.isBefore(now);
        return expiredHold ? SeatStatus.AVAILABLE : status;
    }

    public Long getId() { return id; }
    public Long getEventId() { return eventId; }
    public Long getTierId() { return tierId; }
    public String getSection() { return section; }
    public String getRowLabel() { return rowLabel; }
    public int getSeatNumber() { return seatNumber; }
    public int getX() { return x; }
    public int getY() { return y; }
    public SeatStatus getStatus() { return status; }
    public UUID getHoldId() { return holdId; }
    public Instant getHoldExpiresAt() { return holdExpiresAt; }
}
