package com.festpass.checkin;

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

/** Log of every scan at the gate, valid or not. */
@Entity
@Table(name = "checkin_scans")
public class CheckinScan {

    public enum Result { VALID, ALREADY_USED, INVALID, WRONG_EVENT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id")
    private Long eventId;

    /** Null when the code could not be matched to a ticket. */
    @Column(name = "ticket_id")
    private UUID ticketId;

    @Column(name = "scanned_by")
    private Long scannedBy;

    @Enumerated(EnumType.STRING)
    private Result result;

    @Column(name = "scanned_at")
    private Instant scannedAt;

    protected CheckinScan() {}

    public CheckinScan(Long eventId, UUID ticketId, Long scannedBy, Result result, Instant scannedAt) {
        this.eventId = eventId;
        this.ticketId = ticketId;
        this.scannedBy = scannedBy;
        this.result = result;
        this.scannedAt = scannedAt;
    }

    public Long getId() { return id; }
    public Long getEventId() { return eventId; }
    public UUID getTicketId() { return ticketId; }
    public Long getScannedBy() { return scannedBy; }
    public Result getResult() { return result; }
    public Instant getScannedAt() { return scannedAt; }
}
