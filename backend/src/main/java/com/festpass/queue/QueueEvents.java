package com.festpass.queue;

import java.time.Instant;

/** Application events from the waiting room, picked up by the realtime layer. */
public final class QueueEvents {

    private QueueEvents() {}

    /** Someone joined or a batch was admitted: the waiting counter changed. */
    public record QueueChanged(Long eventId) {}

    /** A user was let in and may now hold seats until {@code expiresAt}. */
    public record Admitted(Long eventId, Long userId, String admissionToken, Instant expiresAt) {}
}
