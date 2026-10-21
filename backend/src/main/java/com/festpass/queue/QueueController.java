package com.festpass.queue;

import com.festpass.auth.AppUserPrincipal;
import com.festpass.queue.WaitingRoom.QueueStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Flash-drop waiting room. Needs a login (see SecurityConfig). */
@RestController
public class QueueController {

    private final WaitingRoom waitingRoom;

    public QueueController(WaitingRoom waitingRoom) {
        this.waitingRoom = waitingRoom;
    }

    /** Joining twice keeps your place. */
    @PostMapping("/api/events/{eventId}/queue")
    public QueueStatus join(@PathVariable Long eventId, @AuthenticationPrincipal AppUserPrincipal me) {
        return waitingRoom.join(eventId, me.id());
    }

    /** Your place, or your admission token once you're in (also pushed to /user/queue/admission). */
    @GetMapping("/api/events/{eventId}/queue/me")
    public QueueStatus me(@PathVariable Long eventId, @AuthenticationPrincipal AppUserPrincipal me) {
        return waitingRoom.status(eventId, me.id());
    }
}
