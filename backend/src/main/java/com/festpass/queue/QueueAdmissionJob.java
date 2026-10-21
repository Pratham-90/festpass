package com.festpass.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every few seconds, lets the next batch of each open flash drop's waiting room in. */
@Component
public class QueueAdmissionJob {

    private static final Logger log = LoggerFactory.getLogger(QueueAdmissionJob.class);

    private final WaitingRoom waitingRoom;

    public QueueAdmissionJob(WaitingRoom waitingRoom) {
        this.waitingRoom = waitingRoom;
    }

    @Scheduled(fixedDelayString = "${festpass.queue.admit-interval}")
    public void run() {
        int admitted = waitingRoom.admitDue();
        if (admitted > 0) {
            log.info("Admitted {} people from waiting rooms", admitted);
        }
    }
}
