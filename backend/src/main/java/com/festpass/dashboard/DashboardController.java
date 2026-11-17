package com.festpass.dashboard;

import com.festpass.auth.AppUserPrincipal;
import com.festpass.dashboard.DashboardService.Dashboard;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Organizer mission control. The role check is in SecurityConfig, the ownership check in the service. */
@RestController
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/api/organizer/events/{eventId}/dashboard")
    public Dashboard dashboard(@PathVariable Long eventId, @AuthenticationPrincipal AppUserPrincipal me) {
        return dashboardService.forEvent(eventId, me);
    }
}
