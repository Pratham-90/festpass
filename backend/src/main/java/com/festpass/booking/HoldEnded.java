package com.festpass.booking;

import java.util.UUID;

/** A hold expired, was cancelled or was paid for. The owner's checkout page is told after commit. */
public record HoldEnded(UUID holdId, Long userId, Long eventId, Hold.Status status) {}
