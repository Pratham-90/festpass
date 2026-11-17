package com.festpass.arena;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public final class ArenaDtos {

    private ArenaDtos() {}

    public static final int MAX_BOTS = 2000;

    public record StartRunRequest(@NotNull Long sourceEventId, @NotNull @Min(1) @Max(MAX_BOTS) Integer bots,
                                  @NotNull ArenaRun.Strategy strategy) {}

    /** A run. {@code result} is the final report once FINISHED (or the error once FAILED). */
    public record RunView(Long id, ArenaRun.Status status, ArenaRun.Strategy strategy, int bots, Long sandboxEventId,
                          Instant startedAt, Instant finishedAt, JsonNode result) {}

    /** Sent on /topic/arena/{runId} every 250 ms while a run is going, and once more at the end. */
    public record Progress(
            Long runId, ArenaRun.Status status, String phase, Long sandboxEventId, int bots,
            long botsLoggedIn, long botsFinished, long elapsedMs,
            long holdAttempts, long holdsWon, long conflicts, long rateLimited, long notAdmitted,
            long ordersPaid, long ticketsBought, long paymentsFailed, long abandoned, long errors,
            long p50Ms, long p95Ms, JsonNode report) {}

    public record ResetResult(long sandboxEventsDeleted, long botsDeleted) {}
}
