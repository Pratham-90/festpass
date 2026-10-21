package com.festpass.payment;

import com.festpass.auth.AppUserPrincipal;
import com.festpass.booking.Hold;
import com.festpass.booking.HoldEnded;
import com.festpass.booking.HoldReleaser;
import com.festpass.booking.HoldRepository;
import com.festpass.booking.Order;
import com.festpass.booking.OrderRepository;
import com.festpass.common.ApiException;
import com.festpass.common.ErrorCode;
import com.festpass.event.AvailabilityChanged;
import com.festpass.seating.SeatStatus;
import com.festpass.seating.SeatStatusWriter;
import com.festpass.seating.SeatsChanged;
import com.festpass.ticket.OrderPaidEvent;
import com.festpass.ticket.TicketIssuer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A fake payment gateway (PLAN.md 5.4). Confirming a payment is idempotent through the
 * Idempotency-Key stored in payment_attempts, and runs with the order row locked:
 * <ol>
 *   <li>a key seen before returns the same result again;</li>
 *   <li>an order that is already PAID is returned unchanged;</li>
 *   <li>if the hold has expired or ended, the order FAILS, the hold is given back and the result is HOLD_EXPIRED;</li>
 *   <li>otherwise the hold is converted, its seats booked, the tickets created and the order marked PAID.</li>
 * </ol>
 */
@Service
public class MockPaymentService {

    public enum Outcome { SUCCESS, DECLINED, HOLD_EXPIRED }

    public enum RequestedOutcome { SUCCESS, FAIL }

    public record PaymentResult(Outcome outcome, Order order) {}

    private final OrderRepository orders;
    private final HoldRepository holds;
    private final SeatStatusWriter seatWriter;
    private final PaymentAttemptRepository attempts;
    private final HoldReleaser releaser;
    private final TicketIssuer ticketIssuer;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final Duration minLatency;
    private final Duration maxLatency;

    public MockPaymentService(OrderRepository orders, HoldRepository holds, SeatStatusWriter seatWriter,
                              PaymentAttemptRepository attempts, HoldReleaser releaser, TicketIssuer ticketIssuer,
                              ApplicationEventPublisher events, TransactionTemplate tx, Clock clock,
                              @Value("${festpass.payment.mock-latency-min}") Duration minLatency,
                              @Value("${festpass.payment.mock-latency-max}") Duration maxLatency) {
        this.orders = orders;
        this.holds = holds;
        this.seatWriter = seatWriter;
        this.attempts = attempts;
        this.releaser = releaser;
        this.ticketIssuer = ticketIssuer;
        this.events = events;
        this.tx = tx;
        this.clock = clock;
        this.minLatency = minLatency;
        this.maxLatency = maxLatency;
    }

    public PaymentResult pay(Long orderId, RequestedOutcome requested, String idempotencyKey, AppUserPrincipal user) {
        simulateGatewayLatency();
        try {
            return tx.execute(status -> attempt(orderId, requested, idempotencyKey, user));
        } catch (HoldLostException e) {
            // The attempt rolled back. Record the failure in a fresh transaction so it sticks.
            return tx.execute(status -> failBecauseHoldLost(orderId, idempotencyKey, user));
        }
    }

    private PaymentResult attempt(Long orderId, RequestedOutcome requested, String key, AppUserPrincipal user) {
        Instant now = Instant.now(clock);
        Order order = lockOwnedOrder(orderId, user);
        Optional<PaymentResult> replay = replay(order, key);
        if (replay.isPresent()) {
            return replay.get();
        }

        if (order.getStatus() == Order.Status.PAID) {
            return record(order, key, Outcome.SUCCESS, now);
        }
        if (order.getStatus() == Order.Status.FAILED) {
            return record(order, key, Outcome.HOLD_EXPIRED, now);
        }
        if (requested == RequestedOutcome.FAIL) {
            // A declined card: the order stays PENDING and can be retried while the hold lasts.
            return record(order, key, Outcome.DECLINED, now);
        }

        Hold hold = holds.findById(order.getHoldId()).orElseThrow();
        if (holds.convert(hold.getId(), now) != 1) {
            throw new HoldLostException();
        }
        if (!hold.isGeneralAdmission()) {
            List<Long> booked = seatWriter.book(hold.getId());
            if (booked.size() != hold.getQuantity()) {
                throw new HoldLostException(); // rolls back the conversion too
            }
            events.publishEvent(new SeatsChanged(order.getEventId(), booked, SeatStatus.BOOKED));
        }
        ticketIssuer.issue(order, hold, user.name(), now);
        order.markPaid(now);
        events.publishEvent(new OrderPaidEvent(order.getId()));
        events.publishEvent(new AvailabilityChanged(order.getEventId()));
        events.publishEvent(new HoldEnded(hold.getId(), hold.getUserId(), hold.getEventId(), Hold.Status.CONVERTED));
        return record(order, key, Outcome.SUCCESS, now);
    }

    private PaymentResult failBecauseHoldLost(Long orderId, String key, AppUserPrincipal user) {
        Instant now = Instant.now(clock);
        Order order = lockOwnedOrder(orderId, user);
        Optional<PaymentResult> replay = replay(order, key);
        if (replay.isPresent()) {
            return replay.get();
        }
        if (order.getStatus() == Order.Status.PAID) {
            return record(order, key, Outcome.SUCCESS, now);
        }
        Hold hold = holds.findById(order.getHoldId()).orElseThrow();
        releaser.end(hold, hold.getExpiresAt().isAfter(now) ? Hold.Status.RELEASED : Hold.Status.EXPIRED);
        order.markFailed();
        return record(order, key, Outcome.HOLD_EXPIRED, now);
    }

    private Order lockOwnedOrder(Long orderId, AppUserPrincipal user) {
        return orders.findByIdForUpdate(orderId)
                .filter(o -> o.getUserId().equals(user.id()))
                .orElseThrow(() -> ApiException.notFound("Order"));
    }

    /** The same key again returns the first answer. The order lock makes concurrent retries queue up. */
    private Optional<PaymentResult> replay(Order order, String key) {
        return attempts.findByIdempotencyKey(key).map(previous -> {
            if (!previous.getOrderId().equals(order.getId())) {
                throw new ApiException(ErrorCode.CONFLICT, "This Idempotency-Key was already used for a different payment");
            }
            return new PaymentResult(Outcome.valueOf(previous.getOutcome()), order);
        });
    }

    private PaymentResult record(Order order, String key, Outcome outcome, Instant now) {
        attempts.save(new PaymentAttempt(order.getId(), key, outcome.name(), now));
        return new PaymentResult(outcome, order);
    }

    private void simulateGatewayLatency() {
        long min = minLatency.toMillis();
        long max = Math.max(min, maxLatency.toMillis());
        if (max == 0) {
            return;
        }
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(min, max + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** The hold expired or was given up between creating the order and paying. */
    private static class HoldLostException extends RuntimeException {
        HoldLostException() {
            super(null, null, false, false);
        }
    }
}
