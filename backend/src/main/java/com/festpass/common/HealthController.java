package com.festpass.common;

import java.time.Clock;
import java.time.Instant;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public HealthController(DataSource dataSource, Clock clock) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.clock = clock;
    }

    @GetMapping
    public HealthResponse health() {
        boolean dbUp;
        try {
            dbUp = Integer.valueOf(1).equals(jdbc.queryForObject("SELECT 1", Integer.class));
        } catch (Exception e) {
            dbUp = false;
        }
        return new HealthResponse(dbUp ? "UP" : "DEGRADED", dbUp ? "UP" : "DOWN", Instant.now(clock));
    }

    public record HealthResponse(String status, String database, Instant time) {}
}
