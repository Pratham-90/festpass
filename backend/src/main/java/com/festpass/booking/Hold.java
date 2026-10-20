package com.festpass.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Seats (or a GA quantity) reserved for a user for a few minutes.
 * Status changes are conditional SQL updates (WHERE status = 'ACTIVE'), so a hold is never released twice.
 */
@Entity
@Table(name = "holds")
public class Hold {

    public enum Status { ACTIVE, CONVERTED, EXPIRED, RELEASED }

    @Id
    private UUID id;

    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "user_id")
    private Long userId;

    /** General admission only. */
    @Column(name = "tier_id")
    private Long tierId;

    /** General admission only. */
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at")
    private Instant createdAt;

    protected Hold() {}

    public Hold(UUID id, Long eventId, Long userId, Long tierId, Integer quantity, Instant expiresAt, Instant createdAt) {
        this.id = id;
        this.eventId = eventId;
        this.userId = userId;
        this.tierId = tierId;
        this.quantity = quantity;
        this.status = Status.ACTIVE;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public Long getEventId() { return eventId; }
    public Long getUserId() { return userId; }
    public Long getTierId() { return tierId; }
    public Integer getQuantity() { return quantity; }
    public Status getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
}
