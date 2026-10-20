package com.festpass.booking;

import com.festpass.auth.AppUserPrincipal;
import com.festpass.booking.BookingDtos.CreateOrderRequest;
import com.festpass.booking.BookingDtos.HoldRequest;
import com.festpass.booking.BookingDtos.HoldView;
import com.festpass.booking.BookingDtos.OrderView;
import com.festpass.common.IdempotencyKeys;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Holds and orders. Any logged-in user can book (see SecurityConfig). */
@RestController
public class BookingController {

    private final HoldService holdService;
    private final OrderService orderService;

    public BookingController(HoldService holdService, OrderService orderService) {
        this.holdService = holdService;
        this.orderService = orderService;
    }

    /** Rate limited per user by HoldRateLimitInterceptor. */
    @PostMapping("/api/events/{eventId}/holds")
    @ResponseStatus(HttpStatus.CREATED)
    public HoldView hold(@PathVariable Long eventId, @Valid @RequestBody HoldRequest request,
                         @AuthenticationPrincipal AppUserPrincipal me) {
        return holdService.hold(eventId, request, me);
    }

    @DeleteMapping("/api/holds/{holdId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@PathVariable UUID holdId, @AuthenticationPrincipal AppUserPrincipal me) {
        holdService.release(holdId, me);
    }

    /** 201 for a new order; 200 with the same order when the request is retried. */
    @PostMapping("/api/orders")
    public ResponseEntity<OrderView> createOrder(@Valid @RequestBody CreateOrderRequest request,
                                                 @RequestHeader(value = IdempotencyKeys.HEADER, required = false) String key,
                                                 @AuthenticationPrincipal AppUserPrincipal me) {
        OrderService.CreatedOrder result = orderService.create(request.holdId(), IdempotencyKeys.require(key), me);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.order());
    }

    @GetMapping("/api/orders/{orderId}")
    public OrderView getOrder(@PathVariable Long orderId, @AuthenticationPrincipal AppUserPrincipal me) {
        return orderService.get(orderId, me);
    }
}
