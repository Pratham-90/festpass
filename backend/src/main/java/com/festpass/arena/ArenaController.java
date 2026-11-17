package com.festpass.arena;

import com.festpass.arena.ArenaDtos.ResetResult;
import com.festpass.arena.ArenaDtos.RunView;
import com.festpass.arena.ArenaDtos.StartRunRequest;
import com.festpass.auth.AppUserPrincipal;
import com.festpass.seating.SeatMapView;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The Stress Arena. Admins only: /api/admin/** (see SecurityConfig). */
@RestController
@RequestMapping("/api/admin/arena")
public class ArenaController {

    private final ArenaService arenaService;

    public ArenaController(ArenaService arenaService) {
        this.arenaService = arenaService;
    }

    /** 202: the run starts in the background. Follow it on /topic/arena/{id} or poll GET /runs/{id}. */
    @PostMapping("/runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RunView start(@Valid @RequestBody StartRunRequest request, @AuthenticationPrincipal AppUserPrincipal me) {
        return arenaService.start(request, me);
    }

    @GetMapping("/runs")
    public List<RunView> recent() {
        return arenaService.recent();
    }

    @GetMapping("/runs/{id}")
    public RunView get(@PathVariable Long id) {
        return arenaService.get(id);
    }

    @GetMapping("/runs/{id}/seatmap")
    public SeatMapView seatMap(@PathVariable Long id) {
        return arenaService.seatMap(id);
    }

    /** Deletes every sandbox event, every bot and every run. Real events and users are untouched. */
    @PostMapping("/reset")
    public ResetResult reset() {
        return arenaService.reset();
    }
}
