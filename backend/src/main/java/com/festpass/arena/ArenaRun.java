package com.festpass.arena;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One Stress Arena run (Phase 6): bots fighting over a sandbox copy of an event. */
@Entity
@Table(name = "arena_runs")
public class ArenaRun {

    public enum Strategy { RUSH_SAME_SEATS, RANDOM_SEATS, GA_RUSH }

    public enum Status { PREPARING, RUNNING, FINISHED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sandbox_event_id")
    private Long sandboxEventId;

    private int bots;

    @Enumerated(EnumType.STRING)
    private Strategy strategy;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json")
    private String resultJson;

    protected ArenaRun() {}

    public ArenaRun(int bots, Strategy strategy) {
        this.bots = bots;
        this.strategy = strategy;
        this.status = Status.PREPARING;
    }

    public Long getId() { return id; }
    public Long getSandboxEventId() { return sandboxEventId; }
    public int getBots() { return bots; }
    public Strategy getStrategy() { return strategy; }
    public Status getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public String getResultJson() { return resultJson; }
}
