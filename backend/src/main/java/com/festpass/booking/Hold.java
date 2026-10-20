package com.festpass.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Seats (or a GA quantity) reserved for a user for a few minutes.
 * Status changes are conditional SQL updates (WHERE status = 'ACTIVE') in {@link HoldRepository},
 * never setters, so a hold is never released or converted twice.
 */
@Entity
@Table(name = "holds")
public class Hold implements Persistable<UUID> {

    public enum Status { ACTIVE, CONVERTED, EXPIRED, RELEASED }

    @Id
    private UUID id;

    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "user_id")
    private Long userId;

    /** General admission only; null for seated holds. */
    @Column(name = "tier_id")
    private Long tierId;

    /** Number of tickets: seats held, or the GA quantity. */
    private int quantity;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Transient
    private boolean isNew = true;

    protected Hold() {}

    public Hold(UUID id, Long eventId, Long userId, Long tierId, int quantity, Instant expiresAt, Instant createdAt) {
        this.id = id;
        this.eventId = eventId;
        this.userId = userId;
        this.tierId = tierId;
        this.quantity = quantity;
        this.status = Status.ACTIVE;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public boolean isGeneralAdmission() {
        return tierId != null;
    }

    @Override
    public UUID getId() { return id; }
    public Long getEventId() { return eventId; }
    public Long getUserId() { return userId; }
    public Long getTierId() { return tierId; }
    public int getQuantity() { return quantity; }
    public Status getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }

    /** Lets save() insert directly instead of first checking whether the id exists. */
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
