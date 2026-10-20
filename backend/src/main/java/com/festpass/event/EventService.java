package com.festpass.event;

import com.festpass.auth.AppUserPrincipal;
import com.festpass.common.ApiException;
import com.festpass.event.EventDtos.EventDetail;
import com.festpass.event.EventDtos.EventRequest;
import com.festpass.event.EventDtos.EventSummary;
import com.festpass.event.EventDtos.GateStaffView;
import com.festpass.event.EventDtos.TierView;
import com.festpass.event.EventEnums.EventCategory;
import com.festpass.event.EventEnums.EventStatus;
import com.festpass.event.EventEnums.SeatingMode;
import com.festpass.seating.SeatRepository;
import com.festpass.user.Role;
import com.festpass.user.User;
import com.festpass.user.UserRepository;
import com.festpass.user.UserService;
import jakarta.persistence.criteria.Predicate;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creating, editing, publishing and listing events. Tiers and seat sections live in {@link EventLayoutService}. */
@Service
public class EventService {

    private final EventRepository events;
    private final TicketTierRepository tiers;
    private final SeatRepository seats;
    private final GateAssignmentRepository gateAssignments;
    private final UserRepository users;
    private final Clock clock;

    public EventService(EventRepository events, TicketTierRepository tiers, SeatRepository seats,
                        GateAssignmentRepository gateAssignments, UserRepository users, Clock clock) {
        this.events = events;
        this.tiers = tiers;
        this.seats = seats;
        this.gateAssignments = gateAssignments;
        this.users = users;
        this.clock = clock;
    }

    // ---- Public ----

    @Transactional(readOnly = true)
    public List<EventSummary> listPublished(String query, EventCategory category, boolean upcomingOnly) {
        Instant now = Instant.now(clock);
        Specification<Event> spec = (root, cq, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.equal(root.get("status"), EventStatus.PUBLISHED));
            where.add(cb.isFalse(root.get("sandbox")));
            if (upcomingOnly) {
                where.add(cb.greaterThan(root.get("endsAt"), now));
            }
            if (category != null) {
                where.add(cb.equal(root.get("category"), category));
            }
            if (query != null && !query.isBlank()) {
                String like = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
                where.add(cb.or(
                        cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(root.get("venueName")), like),
                        cb.like(cb.lower(root.get("description")), like)));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        return summaries(events.findAll(spec, Sort.by("startsAt")));
    }

    @Transactional(readOnly = true)
    public EventDetail getPublishedBySlug(String slug) {
        Event event = events.findBySlug(slug)
                .filter(EventService::isPublic)
                .orElseThrow(() -> ApiException.notFound("Event"));
        return detail(event);
    }

    /** A published, non-sandbox event, or 404. */
    @Transactional(readOnly = true)
    public Event requirePublic(Long eventId) {
        return events.findById(eventId)
                .filter(EventService::isPublic)
                .orElseThrow(() -> ApiException.notFound("Event"));
    }

    /**
     * A published event that tickets can be booked for. Unlike {@link #requirePublic}, Stress Arena
     * sandbox events are allowed, because the bots book through the same API.
     */
    @Transactional(readOnly = true)
    public Event requireBookable(Long eventId) {
        return events.findById(eventId)
                .filter(e -> e.getStatus() == EventStatus.PUBLISHED)
                .orElseThrow(() -> ApiException.notFound("Event"));
    }

    private static boolean isPublic(Event e) {
        return e.getStatus() == EventStatus.PUBLISHED && !e.isSandbox();
    }

    // ---- Organizer ----

    @Transactional(readOnly = true)
    public List<EventSummary> listForOrganizer(AppUserPrincipal actor) {
        List<Event> found = actor.isAdmin()
                ? events.findBySandboxFalseOrderByStartsAtDesc()
                : events.findByOrganizerIdAndSandboxFalseOrderByStartsAtDesc(actor.id());
        return summaries(found);
    }

    @Transactional(readOnly = true)
    public EventDetail getForOrganizer(Long eventId, AppUserPrincipal actor) {
        return detail(requireManageable(eventId, actor));
    }

    @Transactional
    public EventDetail create(EventRequest request, AppUserPrincipal actor) {
        Instant now = Instant.now(clock);
        validateTimes(request);
        if (!request.startsAt().isAfter(now)) {
            throw ApiException.validation("startsAt", "must be in the future");
        }
        Event event = new Event(actor.id(), uniqueSlug(request.title()), request.seatingMode(), now);
        apply(event, request);
        return detail(events.save(event));
    }

    @Transactional
    public EventDetail update(Long eventId, EventRequest request, AppUserPrincipal actor) {
        Event event = requireManageable(eventId, actor);
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw ApiException.invalidState("A cancelled event cannot be edited");
        }
        if (request.seatingMode() != event.getSeatingMode() && tiers.countByEventId(eventId) > 0) {
            throw ApiException.invalidState("The seating mode cannot change once tiers exist");
        }
        validateTimes(request);
        event.setSeatingMode(request.seatingMode());
        apply(event, request);
        return detail(event);
    }

    @Transactional
    public EventDetail publish(Long eventId, AppUserPrincipal actor) {
        Event event = requireManageable(eventId, actor);
        if (event.getStatus() != EventStatus.DRAFT) {
            throw ApiException.invalidState("Only a draft event can be published");
        }
        List<TicketTier> eventTiers = tiers.findByEventIdOrderByIdAsc(eventId);
        if (eventTiers.isEmpty()) {
            throw ApiException.invalidState("Add at least one ticket tier before publishing");
        }
        for (TicketTier tier : eventTiers) {
            if (tier.getCapacity() == 0) {
                throw ApiException.invalidState(event.getSeatingMode() == SeatingMode.SEATED
                        ? "Tier '" + tier.getName() + "' has no seats yet. Add a section for it."
                        : "Tier '" + tier.getName() + "' has no capacity");
            }
        }
        if (!event.getSaleClosesAt().isAfter(Instant.now(clock))) {
            throw ApiException.invalidState("The sale closing time has already passed");
        }
        event.setStatus(EventStatus.PUBLISHED);
        return detail(event);
    }

    @Transactional(readOnly = true)
    public List<GateStaffView> listGateStaff(Long eventId, AppUserPrincipal actor) {
        requireManageable(eventId, actor);
        List<Long> userIds = gateAssignments.findByEventId(eventId).stream().map(GateAssignment::getUserId).toList();
        return users.findAllById(userIds).stream()
                .sorted(Comparator.comparing(User::getName))
                .map(u -> new GateStaffView(u.getId(), u.getName(), u.getEmail()))
                .toList();
    }

    @Transactional
    public GateStaffView assignGateStaff(Long eventId, String email, AppUserPrincipal actor) {
        requireManageable(eventId, actor);
        User staff = users.findByEmail(UserService.normalizeEmail(email))
                .orElseThrow(() -> ApiException.validation("email", "No user with this email"));
        if (staff.getRole() != Role.GATE_STAFF) {
            throw ApiException.validation("email", "This user is not gate staff. An admin can change their role.");
        }
        if (!gateAssignments.existsByUserIdAndEventId(staff.getId(), eventId)) {
            gateAssignments.save(new GateAssignment(staff.getId(), eventId));
        }
        return new GateStaffView(staff.getId(), staff.getName(), staff.getEmail());
    }

    /** The event if the actor may manage it: its organizer, or any admin. */
    @Transactional(readOnly = true)
    public Event requireManageable(Long eventId, AppUserPrincipal actor) {
        Event event = events.findById(eventId)
                .filter(e -> !e.isSandbox())
                .orElseThrow(() -> ApiException.notFound("Event"));
        checkCanManage(event, actor);
        return event;
    }

    static void checkCanManage(Event event, AppUserPrincipal actor) {
        if (!actor.isAdmin() && !event.getOrganizerId().equals(actor.id())) {
            throw ApiException.forbidden("You can only manage your own events");
        }
    }

    // ---- Views ----

    /** Tiers with how many tickets are still available. */
    @Transactional(readOnly = true)
    public List<TierView> tierViews(Event event) {
        List<TicketTier> eventTiers = tiers.findByEventIdOrderByIdAsc(event.getId());
        Map<Long, Long> freeSeats = event.getSeatingMode() == SeatingMode.SEATED
                ? seats.countAvailableByTier(event.getId(), Instant.now(clock)).stream()
                    .collect(Collectors.toMap(SeatRepository.TierAvailability::getTierId,
                            SeatRepository.TierAvailability::getAvailable))
                : Map.of();
        return eventTiers.stream().map(t -> new TierView(
                t.getId(), t.getName(), t.getPricePaise(), t.getCapacity(),
                event.getSeatingMode() == SeatingMode.SEATED
                        ? freeSeats.getOrDefault(t.getId(), 0L).intValue()
                        : t.getCapacity() - t.getReserved(),
                t.getColorHex())).toList();
    }

    private EventDetail detail(Event e) {
        List<TierView> tierViews = tierViews(e);
        String organizerName = users.findById(e.getOrganizerId()).map(User::getName).orElse(null);
        String accent = tierViews.isEmpty() ? null : tierViews.get(0).colorHex();
        return new EventDetail(e.getId(), e.getSlug(), e.getTitle(), e.getDescription(), e.getCategory(),
                e.getBannerUrl(), e.getVenueName(), e.getStartsAt(), e.getEndsAt(), e.getSaleOpensAt(),
                e.getSaleClosesAt(), e.getStatus(), e.getSeatingMode(), e.getHoldMinutes(), e.getMaxTicketsPerUser(),
                e.isFlashDrop(), organizerName, accent, tierViews);
    }

    /** Summaries with the cheapest price and accent colour, loading all tiers in one query. */
    private List<EventSummary> summaries(List<Event> found) {
        Map<Long, List<TicketTier>> tiersByEvent = tiers.findByEventIdIn(found.stream().map(Event::getId).toList())
                .stream()
                .sorted(Comparator.comparing(TicketTier::getId))
                .collect(Collectors.groupingBy(TicketTier::getEventId));
        return found.stream().map(e -> {
            List<TicketTier> eventTiers = tiersByEvent.getOrDefault(e.getId(), List.of());
            Long fromPrice = eventTiers.stream().map(TicketTier::getPricePaise).min(Long::compare).orElse(null);
            String accent = eventTiers.isEmpty() ? null : eventTiers.get(0).getColorHex();
            return new EventSummary(e.getId(), e.getSlug(), e.getTitle(), e.getCategory(), e.getBannerUrl(),
                    e.getVenueName(), e.getStartsAt(), e.getEndsAt(), e.getSaleOpensAt(), e.getSaleClosesAt(),
                    e.getStatus(), e.getSeatingMode(), e.isFlashDrop(), fromPrice, accent);
        }).toList();
    }

    // ---- Helpers ----

    private static void validateTimes(EventRequest r) {
        if (!r.endsAt().isAfter(r.startsAt())) {
            throw ApiException.validation("endsAt", "must be after the start time");
        }
        if (!r.saleClosesAt().isAfter(r.saleOpensAt())) {
            throw ApiException.validation("saleClosesAt", "must be after the sale opening time");
        }
        if (r.saleClosesAt().isAfter(r.endsAt())) {
            throw ApiException.validation("saleClosesAt", "must not be after the event ends");
        }
    }

    private static void apply(Event event, EventRequest r) {
        event.setTitle(r.title().trim());
        event.setDescription(r.description());
        event.setCategory(r.category());
        event.setBannerUrl(r.bannerUrl());
        event.setVenueName(r.venueName().trim());
        event.setStartsAt(r.startsAt());
        event.setEndsAt(r.endsAt());
        event.setSaleOpensAt(r.saleOpensAt());
        event.setSaleClosesAt(r.saleClosesAt());
        event.setHoldMinutes(Objects.requireNonNullElse(r.holdMinutes(), 5));
        event.setMaxTicketsPerUser(Objects.requireNonNullElse(r.maxTicketsPerUser(), 4));
        event.setFlashDrop(Boolean.TRUE.equals(r.flashDrop()));
    }

    private String uniqueSlug(String title) {
        String base = slugify(title);
        String slug = base;
        for (int i = 2; events.existsBySlug(slug); i++) {
            slug = base + "-" + i;
        }
        return slug;
    }

    static String slugify(String text) {
        String ascii = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        if (slug.length() > 100) {
            slug = slug.substring(0, 100).replaceAll("-+$", "");
        }
        return slug.isEmpty() ? "event" : slug;
    }
}
