package com.wxl.testdataagent.health;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class HealthService {

    private final JdbcTemplate jdbcTemplate;

    public HealthService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public HealthResponse inspect() {
        String databaseStatus;
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            databaseStatus = result != null && result == 1 ? "UP" : "DEGRADED";
        } catch (Exception exception) {
            databaseStatus = "DOWN";
        }

        String serviceStatus = "UP".equals(databaseStatus) ? "UP" : "DEGRADED";
        return new HealthResponse(
                serviceStatus,
                "testdata-agent-backend",
                "0.1.0",
                databaseStatus,
                Instant.now()
        );
    }
}

