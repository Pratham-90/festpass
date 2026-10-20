package com.festpass.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A price level of an event. For SEATED events the capacity is the number of seats
 * in the tier (grows as sections are added). For GENERAL events, {@code reserved}
 * counts held + sold tickets and is only changed by conditional SQL updates (see booking).
 */
@Entity
@Table(name = "ticket_tiers")
public class TicketTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id")
    private Long eventId;

    private String name;

    @Column(name = "price_paise")
    private long pricePaise;

    private int capacity;

    private int reserved;

    @Column(name = "color_hex")
    private String colorHex;

    protected TicketTier() {}

    public TicketTier(Long eventId, String name, long pricePaise, int capacity, String colorHex) {
        this.eventId = eventId;
        this.name = name;
        this.pricePaise = pricePaise;
        this.capacity = capacity;
        this.colorHex = colorHex;
    }

    public Long getId() { return id; }
    public Long getEventId() { return eventId; }
    public String getName() { return name; }
    public long getPricePaise() { return pricePaise; }
    public int getCapacity() { return capacity; }
    public int getReserved() { return reserved; }
    public String getColorHex() { return colorHex; }
}
