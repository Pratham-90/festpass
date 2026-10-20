package com.festpass.event;

import com.festpass.event.EventEnums.EventCategory;
import com.festpass.event.EventEnums.EventStatus;
import com.festpass.event.EventEnums.SeatingMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** Request and response records for the event API. */
public final class EventDtos {

    private EventDtos() {}

    // ---- Requests ----

    /** Used for both create and update. Optional fields fall back to the defaults in PLAN.md. */
    public record EventRequest(
            @NotBlank @Size(max = 150) String title,
            @Size(max = 5000) String description,
            @NotNull EventCategory category,
            @Size(max = 500) @Pattern(regexp = "^https?://.+", message = "must be an http(s) URL") String bannerUrl,
            @NotBlank @Size(max = 150) String venueName,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt,
            @NotNull Instant saleOpensAt,
            @NotNull Instant saleClosesAt,
            @NotNull SeatingMode seatingMode,
            @Min(1) @Max(30) Integer holdMinutes,
            @Min(1) @Max(20) Integer maxTicketsPerUser,
            Boolean flashDrop) {}

    /** {@code capacity} is required for GENERAL events and must be left out for SEATED ones (seats decide it). */
    public record TierRequest(
            @NotBlank @Size(max = 60) String name,
            @NotNull @PositiveOrZero @Max(10_000_000) Long pricePaise,
            @Min(1) @Max(100_000) Integer capacity,
            @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "must look like #ff3d81") String colorHex) {}

    public record SectionRequest(
            @NotBlank @Size(max = 40) String name,
            @NotNull Long tierId,
            @NotNull @Min(1) @Max(52) Integer rows,
            @NotNull @Min(1) @Max(60) Integer seatsPerRow,
            List<@NotNull Integer> aisleAfter) {}

    public record GateStaffRequest(@NotBlank String email) {}

    // ---- Responses ----

    public record TierView(Long id, String name, long pricePaise, int capacity, int remaining, String colorHex) {}

    public record EventSummary(
            Long id, String slug, String title, EventCategory category, String bannerUrl, String venueName,
            Instant startsAt, Instant endsAt, Instant saleOpensAt, Instant saleClosesAt,
            EventStatus status, SeatingMode seatingMode, boolean flashDrop,
            Long fromPricePaise, String accentColor) {}

    public record EventDetail(
            Long id, String slug, String title, String description, EventCategory category, String bannerUrl,
            String venueName, Instant startsAt, Instant endsAt, Instant saleOpensAt, Instant saleClosesAt,
            EventStatus status, SeatingMode seatingMode, int holdMinutes, int maxTicketsPerUser, boolean flashDrop,
            String organizerName, String accentColor, List<TierView> tiers) {}

    public record SectionView(String name, Long tierId, int seatCount, int minX, int minY, int maxX, int maxY) {}

    public record GateStaffView(Long userId, String name, String email) {}
}
