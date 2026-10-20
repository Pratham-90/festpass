package com.festpass.event;

import com.festpass.event.EventEnums.EventCategory;
import com.festpass.event.EventEnums.EventStatus;
import com.festpass.event.EventEnums.SeatingMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organizer_id")
    private Long organizerId;

    private String slug;
    private String title;
    private String description;

    @Enumerated(EnumType.STRING)
    private EventCategory category;

    @Column(name = "banner_url")
    private String bannerUrl;

    @Column(name = "venue_name")
    private String venueName;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "sale_opens_at")
    private Instant saleOpensAt;

    @Column(name = "sale_closes_at")
    private Instant saleClosesAt;

    @Enumerated(EnumType.STRING)
    private EventStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "seating_mode")
    private SeatingMode seatingMode;

    @Column(name = "hold_minutes")
    private int holdMinutes;

    @Column(name = "max_tickets_per_user")
    private int maxTicketsPerUser;

    @Column(name = "flash_drop")
    private boolean flashDrop;

    @Column(name = "is_sandbox")
    private boolean sandbox;

    @Column(name = "created_at")
    private Instant createdAt;

    protected Event() {}

    public Event(Long organizerId, String slug, SeatingMode seatingMode, Instant createdAt) {
        this.organizerId = organizerId;
        this.slug = slug;
        this.seatingMode = seatingMode;
        this.status = EventStatus.DRAFT;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getOrganizerId() { return organizerId; }
    public String getSlug() { return slug; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public EventCategory getCategory() { return category; }
    public String getBannerUrl() { return bannerUrl; }
    public String getVenueName() { return venueName; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public Instant getSaleOpensAt() { return saleOpensAt; }
    public Instant getSaleClosesAt() { return saleClosesAt; }
    public EventStatus getStatus() { return status; }
    public SeatingMode getSeatingMode() { return seatingMode; }
    public int getHoldMinutes() { return holdMinutes; }
    public int getMaxTicketsPerUser() { return maxTicketsPerUser; }
    public boolean isFlashDrop() { return flashDrop; }
    public boolean isSandbox() { return sandbox; }
    public Instant getCreatedAt() { return createdAt; }

    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setCategory(EventCategory category) { this.category = category; }
    public void setBannerUrl(String bannerUrl) { this.bannerUrl = bannerUrl; }
    public void setVenueName(String venueName) { this.venueName = venueName; }
    public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }
    public void setEndsAt(Instant endsAt) { this.endsAt = endsAt; }
    public void setSaleOpensAt(Instant saleOpensAt) { this.saleOpensAt = saleOpensAt; }
    public void setSaleClosesAt(Instant saleClosesAt) { this.saleClosesAt = saleClosesAt; }
    public void setStatus(EventStatus status) { this.status = status; }
    public void setSeatingMode(SeatingMode seatingMode) { this.seatingMode = seatingMode; }
    public void setHoldMinutes(int holdMinutes) { this.holdMinutes = holdMinutes; }
    public void setMaxTicketsPerUser(int maxTicketsPerUser) { this.maxTicketsPerUser = maxTicketsPerUser; }
    public void setFlashDrop(boolean flashDrop) { this.flashDrop = flashDrop; }
    public void setSandbox(boolean sandbox) { this.sandbox = sandbox; }
}
