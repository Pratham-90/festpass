package com.festpass.seating;

import com.festpass.auth.AppUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SeatMapController {

    private final SeatMapService seatMapService;

    public SeatMapController(SeatMapService seatMapService) {
        this.seatMapService = seatMapService;
    }

    @GetMapping("/api/events/{id}/seatmap")
    public SeatMapView publicSeatMap(@PathVariable Long id) {
        return seatMapService.forPublicEvent(id);
    }

    @GetMapping("/api/organizer/events/{id}/seatmap")
    public SeatMapView organizerSeatMap(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal me) {
        return seatMapService.forOrganizer(id, me);
    }
}
