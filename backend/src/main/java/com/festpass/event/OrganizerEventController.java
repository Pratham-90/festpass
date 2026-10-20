package com.festpass.event;

import com.festpass.auth.AppUserPrincipal;
import com.festpass.event.EventDtos.EventDetail;
import com.festpass.event.EventDtos.EventRequest;
import com.festpass.event.EventDtos.EventSummary;
import com.festpass.event.EventDtos.GateStaffRequest;
import com.festpass.event.EventDtos.GateStaffView;
import com.festpass.event.EventDtos.SectionRequest;
import com.festpass.event.EventDtos.SectionView;
import com.festpass.event.EventDtos.TierRequest;
import com.festpass.event.EventDtos.TierView;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Event management for ORGANIZER (own events) and ADMIN (any event).
 * The role check is in SecurityConfig; the ownership check is in the services.
 */
@RestController
@RequestMapping("/api/organizer/events")
public class OrganizerEventController {

    private final EventService eventService;
    private final EventLayoutService layoutService;

    public OrganizerEventController(EventService eventService, EventLayoutService layoutService) {
        this.eventService = eventService;
        this.layoutService = layoutService;
    }

    @GetMapping
    public List<EventSummary> list(@AuthenticationPrincipal AppUserPrincipal me) {
        return eventService.listForOrganizer(me);
    }

    @GetMapping("/{id}")
    public EventDetail get(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal me) {
        return eventService.getForOrganizer(id, me);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventDetail create(@Valid @RequestBody EventRequest request, @AuthenticationPrincipal AppUserPrincipal me) {
        return eventService.create(request, me);
    }

    @PutMapping("/{id}")
    public EventDetail update(@PathVariable Long id, @Valid @RequestBody EventRequest request,
                              @AuthenticationPrincipal AppUserPrincipal me) {
        return eventService.update(id, request, me);
    }

    @PostMapping("/{id}/publish")
    public EventDetail publish(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal me) {
        return eventService.publish(id, me);
    }

    @PostMapping("/{id}/tiers")
    @ResponseStatus(HttpStatus.CREATED)
    public TierView addTier(@PathVariable Long id, @Valid @RequestBody TierRequest request,
                            @AuthenticationPrincipal AppUserPrincipal me) {
        return layoutService.addTier(id, request, me);
    }

    @PostMapping("/{id}/sections")
    @ResponseStatus(HttpStatus.CREATED)
    public SectionView addSection(@PathVariable Long id, @Valid @RequestBody SectionRequest request,
                                  @AuthenticationPrincipal AppUserPrincipal me) {
        return layoutService.addSection(id, request, me);
    }

    @GetMapping("/{id}/gate-staff")
    public List<GateStaffView> gateStaff(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal me) {
        return eventService.listGateStaff(id, me);
    }

    @PostMapping("/{id}/gate-staff")
    public GateStaffView assignGateStaff(@PathVariable Long id, @Valid @RequestBody GateStaffRequest request,
                                         @AuthenticationPrincipal AppUserPrincipal me) {
        return eventService.assignGateStaff(id, request.email(), me);
    }
}
