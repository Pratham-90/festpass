package com.festpass.booking;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request and response records for holds and orders. */
public final class BookingDtos {

    private BookingDtos() {}

    /** Seated events send {@code seatIds}; general-admission events send {@code tierId} and {@code quantity}. */
    public record HoldRequest(
            @Size(max = 20) List<@NotNull Long> seatIds,
            Long tierId,
            @Min(1) @Max(20) Integer quantity) {}

    public record CreateOrderRequest(@NotNull UUID holdId) {}

    public record SeatRef(Long id, String section, String row, int number, Long tierId) {

        public String label() {
            return section + " · Row " + row + " · Seat " + number;
        }
    }

    public record HoldView(
            UUID holdId, Long eventId, Hold.Status status, Instant expiresAt, int quantity,
            Long tierId, List<SeatRef> seats) {}

    public record OrderItem(Long tierId, String tierName, long unitPricePaise, int quantity, SeatRef seat) {}

    public record OrderView(
            Long id, Long eventId, String eventSlug, String eventTitle, UUID holdId, Order.Status status, long amountPaise,
            Instant createdAt, Instant paidAt, Instant holdExpiresAt, List<OrderItem> items, List<UUID> ticketIds) {}
}
