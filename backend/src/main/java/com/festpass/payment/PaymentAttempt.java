package com.festpass.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** One mock payment confirmation. The unique idempotency key makes retries safe. */
@Entity
@Table(name = "payment_attempts")
public class PaymentAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    private String outcome;

    @Column(name = "created_at")
    private Instant createdAt;

    protected PaymentAttempt() {}

    public PaymentAttempt(Long orderId, String idempotencyKey, String outcome, Instant createdAt) {
        this.orderId = orderId;
        this.idempotencyKey = idempotencyKey;
        this.outcome = outcome;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getOutcome() { return outcome; }
    public Instant getCreatedAt() { return createdAt; }
}
