package com.festpass.event;

/**
 * Remaining or sold counts of an event changed (GA tickets held or given back, a payment completed).
 * The realtime layer refreshes the event's live stats after the transaction commits.
 */
public record AvailabilityChanged(Long eventId) {}
