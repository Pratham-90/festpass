package com.festpass.realtime;

import com.festpass.booking.Hold;
import com.festpass.checkin.CheckinScan;
import com.festpass.seating.SeatStatus;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Payloads sent over WebSocket (see docs/api.md, "Real-time"). */
public final class RealtimeMessages {

    private RealtimeMessages() {}

    /** One entry of the batch on /topic/events/{id}/seats. */
    public record SeatDelta(Long seatId, SeatStatus status) {}

    /** /topic/events/{id}/stats */
    public record EventStats(Long eventId, int viewers, Map<Long, Integer> remainingByTier, long soldLastMinute) {}

    /** /user/queue/holds */
    public record HoldUpdate(UUID holdId, Long eventId, Hold.Status status) {}

    /** /user/queue/admission */
    public record AdmissionMessage(Long eventId, String admissionToken, Instant expiresAt) {}

    /** /topic/events/{id}/checkins */
    public record CheckinUpdate(Long eventId, long checkedIn, long total, LastScan lastScan) {}

    public record LastScan(CheckinScan.Result result, String tierName, String seatLabel, Instant at) {}

    /** Destinations, in one place. */
    static String seats(Long eventId) { return "/topic/events/" + eventId + "/seats"; }
    static String stats(Long eventId) { return "/topic/events/" + eventId + "/stats"; }
    static String queue(Long eventId) { return "/topic/events/" + eventId + "/queue"; }
    static String checkins(Long eventId) { return "/topic/events/" + eventId + "/checkins"; }
    static final String USER_HOLDS = "/queue/holds";
    static final String USER_ADMISSION = "/queue/admission";
}
