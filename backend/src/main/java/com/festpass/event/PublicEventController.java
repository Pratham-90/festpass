package com.festpass.event;

import com.festpass.event.EventDtos.EventDetail;
import com.festpass.event.EventDtos.EventSummary;
import com.festpass.event.EventEnums.EventCategory;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Published events, visible to everyone. The seat map is in seating.SeatMapController. */
@RestController
@RequestMapping("/api/events")
public class PublicEventController {

    private final EventService eventService;

    public PublicEventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    public List<EventSummary> list(@RequestParam(required = false) String q,
                                   @RequestParam(required = false) EventCategory category,
                                   @RequestParam(defaultValue = "true") boolean upcoming) {
        return eventService.listPublished(q, category, upcoming);
    }

    @GetMapping("/{slug}")
    public EventDetail get(@PathVariable String slug) {
        return eventService.getPublishedBySlug(slug);
    }
}
