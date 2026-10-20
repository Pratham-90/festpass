package com.festpass.ticket;

/** Published inside the payment transaction; listeners that send email run only after it commits. */
public record OrderPaidEvent(Long orderId) {}
