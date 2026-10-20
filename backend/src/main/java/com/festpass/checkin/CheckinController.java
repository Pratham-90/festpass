package com.festpass.checkin;

import com.festpass.auth.AppUserPrincipal;
import com.festpass.checkin.CheckinService.CheckinStats;
import com.festpass.checkin.CheckinService.ScanResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CheckinController {

    private final CheckinService checkinService;

    public CheckinController(CheckinService checkinService) {
        this.checkinService = checkinService;
    }

    public record ScanRequest(@NotNull Long eventId, @NotBlank @Size(max = 200) String code) {}

    /** Always 200: VALID, ALREADY_USED, INVALID and WRONG_EVENT are normal scan results, not errors. */
    @PostMapping("/api/checkin")
    public ScanResponse scan(@Valid @RequestBody ScanRequest request, @AuthenticationPrincipal AppUserPrincipal me) {
        return checkinService.scan(request.eventId(), request.code(), me);
    }

    @GetMapping("/api/events/{eventId}/checkin/stats")
    public CheckinStats stats(@PathVariable Long eventId, @AuthenticationPrincipal AppUserPrincipal me) {
        return checkinService.stats(eventId, me);
    }
}
