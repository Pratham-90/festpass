package com.festpass.ticket;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * A ticket. The database allows at most one ticket per seat (partial unique index on seat_id),
 * and check-in is a conditional update on checked_in_at IS NULL.
 */
@Entity
@Table(name = "tickets")
public class Ticket implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "tier_id")
    private Long tierId;

    /** Null for general admission. */
    @Column(name = "seat_id")
    private Long seatId;

    @Column(name = "holder_name")
    private String holderName;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @Column(name = "checked_in_by")
    private Long checkedInBy;

    @Column(name = "created_at")
    private Instant createdAt;

    @Transient
    private boolean isNew = true;

    protected Ticket() {}

    public Ticket(UUID id, Long orderId, Long eventId, Long tierId, Long seatId, String holderName, Instant createdAt) {
        this.id = id;
        this.orderId = orderId;
        this.eventId = eventId;
        this.tierId = tierId;
        this.seatId = seatId;
        this.holderName = holderName;
        this.createdAt = createdAt;
    }

    @Override
    public UUID getId() { return id; }
    public Long getOrderId() { return orderId; }
    public Long getEventId() { return eventId; }
    public Long getTierId() { return tierId; }
    public Long getSeatId() { return seatId; }
    public String getHolderName() { return holderName; }
    public Instant getCheckedInAt() { return checkedInAt; }
    public Long getCheckedInBy() { return checkedInBy; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        isNew = false;
    }
}
