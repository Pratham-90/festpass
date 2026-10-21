package com.festpass.seating;

import java.util.List;

/**
 * Some seats of an event changed status. Published inside the booking transaction;
 * the realtime layer sends it to browsers only after the transaction commits.
 */
public record SeatsChanged(Long eventId, List<Long> seatIds, SeatStatus status) {}
