package com.festpass.checkin;

import java.time.Instant;

/** A gate scan was logged. The gate screens and dashboard get updated counts after commit. */
public record CheckinRecorded(Long eventId, CheckinScan.Result result, String tierName, String seatLabel, Instant at) {}
