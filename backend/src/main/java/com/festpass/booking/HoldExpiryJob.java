package com.festpass.booking;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Every few seconds, ends holds that have expired and gives their seats or GA tickets back
 * (PLAN.md 5.5). Correctness never depends on this job: an expired hold already counts as free
 * when someone else tries to hold the seat. The job just tidies up and keeps the counts accurate.
 */
@Component
public class HoldExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryJob.class);
    private static final int BATCH = 500;

    private final HoldRepository holds;
    private final HoldReleaser releaser;
    private final TransactionTemplate tx;
    private final Clock clock;

    public HoldExpiryJob(HoldRepository holds, HoldReleaser releaser, TransactionTemplate tx, Clock clock) {
        this.holds = holds;
        this.releaser = releaser;
        this.tx = tx;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${festpass.holds.expiry-interval}")
    public void run() {
        int expired = expireDue();
        if (expired > 0) {
            log.info("Expired {} hold(s)", expired);
        }
    }

    /** @return how many holds this run ended. Each hold gets its own short transaction. */
    public int expireDue() {
        Instant now = Instant.now(clock);
        int expired = 0;
        List<UUID> due;
        do {
            due = holds.findExpiredIds(now, BATCH);
            for (UUID id : due) {
                Boolean ended = tx.execute(status -> holds.findById(id)
                        .map(hold -> releaser.end(hold, Hold.Status.EXPIRED))
                        .orElse(false));
                if (Boolean.TRUE.equals(ended)) {
                    expired++;
                }
            }
        } while (due.size() == BATCH);
        return expired;
    }
}
