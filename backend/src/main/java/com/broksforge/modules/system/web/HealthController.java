package com.broksforge.modules.system.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Lightweight, public liveness endpoint for the frontend and uptime checks.
 * Deep health (DB, Redis, disk) is exposed separately by Spring Actuator at
 * {@code /actuator/health}.
 */
@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "System", description = "Service health and metadata")
public class HealthController {

    private final String applicationName;

    public HealthController(@Value("${spring.application.name:broks-forge-api}") String applicationName) {
        this.applicationName = applicationName;
    }

    @GetMapping
    @Operation(summary = "Liveness check", description = "Returns 200 with basic service metadata.")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", applicationName,
                "version", "0.1.0",
                "timestamp", Instant.now().toString()
        ));
    }
}
