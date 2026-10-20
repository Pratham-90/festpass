package com.festpass.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;

/** Gate staff member allowed to scan tickets for an event. */
@Entity
@Table(name = "gate_assignments")
@IdClass(GateAssignment.Key.class)
public class GateAssignment {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "event_id")
    private Long eventId;

    protected GateAssignment() {}

    public GateAssignment(Long userId, Long eventId) {
        this.userId = userId;
        this.eventId = eventId;
    }

    public Long getUserId() { return userId; }
    public Long getEventId() { return eventId; }

    public record Key(Long userId, Long eventId) implements Serializable {}
}
