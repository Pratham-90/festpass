package com.festpass.ticket;

import com.festpass.auth.AppUserPrincipal;
import com.festpass.ticket.PassportService.Passport;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PassportController {

    private final PassportService passportService;

    public PassportController(PassportService passportService) {
        this.passportService = passportService;
    }

    @GetMapping("/api/me/passport")
    public Passport passport(@AuthenticationPrincipal AppUserPrincipal me) {
        return passportService.forUser(me);
    }
}
