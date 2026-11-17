package com.festpass.arena;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.festpass.arena.ArenaDtos.ResetResult;
import com.festpass.arena.ArenaDtos.RunView;
import com.festpass.arena.ArenaDtos.StartRunRequest;
import com.festpass.auth.AppUserPrincipal;
import com.festpass.common.ApiException;
import com.festpass.event.Event;
import com.festpass.event.EventEnums.SeatingMode;
import com.festpass.event.EventRepository;
import com.festpass.event.EventService;
import com.festpass.seating.SeatMapService;
import com.festpass.seating.SeatMapView;
import java.util.List;
import org.springframework.stereotype.Service;

/** Starts, reports on and resets Stress Arena runs. Only admins get here (see SecurityConfig). */
@Service
public class ArenaService {

    private static final List<ArenaRun.Status> IN_PROGRESS = List.of(ArenaRun.Status.PREPARING, ArenaRun.Status.RUNNING);

    private final ArenaRunRepository runs;
    private final ArenaRunner runner;
    private final ArenaSandbox sandbox;
    private final EventService eventService;
    private final EventRepository events;
    private final SeatMapService seatMaps;
    private final ObjectMapper json;

    public ArenaService(ArenaRunRepository runs, ArenaRunner runner, ArenaSandbox sandbox, EventService eventService,
                        EventRepository events, SeatMapService seatMaps, ObjectMapper json) {
        this.runs = runs;
        this.runner = runner;
        this.sandbox = sandbox;
        this.eventService = eventService;
        this.events = events;
        this.seatMaps = seatMaps;
        this.json = json;
    }

    /** Validates the request, records the run and starts it in the background. One run at a time. */
    public synchronized RunView start(StartRunRequest request, AppUserPrincipal admin) {
        Event source = eventService.requirePublic(request.sourceEventId());
        boolean seated = source.getSeatingMode() == SeatingMode.SEATED;
        if (request.strategy() == ArenaRun.Strategy.GA_RUSH && seated) {
            throw ApiException.validation("strategy", "GA_RUSH needs a general-admission event");
        }
        if (request.strategy() != ArenaRun.Strategy.GA_RUSH && !seated) {
            throw ApiException.validation("strategy", "This is a general-admission event: use GA_RUSH");
        }
        if (runs.existsByStatusIn(IN_PROGRESS)) {
            throw ApiException.invalidState("A run is already in progress. Wait for it to finish.");
        }
        ArenaRun run = runs.save(new ArenaRun(request.bots(), request.strategy()));
        long runId = run.getId();
        Thread.ofVirtual().name("arena-run-" + runId)
                .start(() -> runner.run(runId, source.getId(), admin.id()));
        return view(run);
    }

    public RunView get(Long runId) {
        return view(runs.findById(runId).orElseThrow(() -> ApiException.notFound("Arena run")));
    }

    public List<RunView> recent() {
        return runs.findTop10ByOrderByIdDesc().stream().map(this::view).toList();
    }

    /** The live seat map of a run's sandbox event. */
    public SeatMapView seatMap(Long runId) {
        ArenaRun run = runs.findById(runId).orElseThrow(() -> ApiException.notFound("Arena run"));
        Event copy = run.getSandboxEventId() == null ? null : events.findById(run.getSandboxEventId()).orElse(null);
        if (copy == null) {
            throw ApiException.notFound("Sandbox event");
        }
        return seatMaps.forSandbox(copy);
    }

    public synchronized ResetResult reset() {
        if (runs.existsByStatusIn(IN_PROGRESS)) {
            throw ApiException.invalidState("A run is in progress. Reset after it finishes.");
        }
        long[] deleted = sandbox.reset();
        return new ResetResult(deleted[0], deleted[1]);
    }

    private RunView view(ArenaRun run) {
        JsonNode result = null;
        if (run.getResultJson() != null) {
            try {
                result = json.readTree(run.getResultJson());
            } catch (JsonProcessingException e) {
                result = json.createObjectNode().put("error", "Unreadable result");
            }
        }
        return new RunView(run.getId(), run.getStatus(), run.getStrategy(), run.getBots(), run.getSandboxEventId(),
                run.getStartedAt(), run.getFinishedAt(), result);
    }
}
