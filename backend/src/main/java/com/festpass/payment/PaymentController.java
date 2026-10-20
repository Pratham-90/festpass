package com.festpass.payment;

import com.festpass.auth.AppUserPrincipal;
import com.festpass.booking.BookingDtos.OrderView;
import com.festpass.booking.OrderService;
import com.festpass.common.ApiException;
import com.festpass.common.ErrorCode;
import com.festpass.common.IdempotencyKeys;
import com.festpass.payment.MockPaymentService.PaymentResult;
import com.festpass.payment.MockPaymentService.RequestedOutcome;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController {

    private final MockPaymentService paymentService;
    private final OrderService orderService;

    public PaymentController(MockPaymentService paymentService, OrderService orderService) {
        this.paymentService = paymentService;
        this.orderService = orderService;
    }

    public record MockPaymentRequest(@NotNull RequestedOutcome outcome) {}

    /** 200 with the paid order (and its ticket ids); 402 PAYMENT_DECLINED; 409 HOLD_EXPIRED. Retries give the same answer. */
    @PostMapping("/api/payments/mock/{orderId}")
    public OrderView pay(@PathVariable Long orderId, @Valid @RequestBody MockPaymentRequest request,
                         @RequestHeader(value = IdempotencyKeys.HEADER, required = false) String key,
                         @AuthenticationPrincipal AppUserPrincipal me) {
        PaymentResult result = paymentService.pay(orderId, request.outcome(), IdempotencyKeys.require(key), me);
        Map<String, Object> details = Map.of("orderId", orderId, "orderStatus", result.order().getStatus());
        return switch (result.outcome()) {
            case SUCCESS -> orderService.view(result.order());
            case DECLINED -> throw new ApiException(ErrorCode.PAYMENT_DECLINED,
                    "The payment was declined. You can try again while your seats are held.", details);
            case HOLD_EXPIRED -> throw new ApiException(ErrorCode.HOLD_EXPIRED,
                    "Your hold expired before the payment went through. You have not been charged.", details);
        };
    }
}
