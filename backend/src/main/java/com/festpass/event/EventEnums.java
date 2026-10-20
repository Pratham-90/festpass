package com.festpass.event;

/** Small enums used by events. Kept together so they are easy to find. */
public final class EventEnums {

    private EventEnums() {}

    public enum EventStatus { DRAFT, PUBLISHED, CANCELLED }

    public enum SeatingMode { SEATED, GENERAL }

    public enum EventCategory { MUSIC, DANCE, TECH, COMEDY, DRAMA, SPORTS, WORKSHOP, OTHER }
}
