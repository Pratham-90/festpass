package com.festpass.booking;

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
 * One order per hold (unique hold_id); retries with the same idempotency key return the same order.
 * Status changes only happen while the order row is locked (SELECT ... FOR UPDATE).
 */
@Entity
@Table(name = "orders")
public class Order {

    public enum Status { PENDING, PAID, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "hold_id")
    private UUID holdId;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "amount_paise")
    private long amountPaise;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    protected Order() {}

    public Order(Long userId, Long eventId, UUID holdId, String idempotencyKey, long amountPaise, Instant createdAt) {
        this.userId = userId;
        this.eventId = eventId;
        this.holdId = holdId;
        this.idempotencyKey = idempotencyKey;
        this.amountPaise = amountPaise;
        this.status = Status.PENDING;
        this.createdAt = createdAt;
    }

    public void markPaid(Instant now) {
        this.status = Status.PAID;
        this.paidAt = now;
    }

    public void markFailed() {
        this.status = Status.FAILED;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getEventId() { return eventId; }
    public UUID getHoldId() { return holdId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public long getAmountPaise() { return amountPaise; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPaidAt() { return paidAt; }
}
