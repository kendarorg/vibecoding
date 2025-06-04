package org.kendar.sync.server.api.controller;

import jakarta.annotation.security.PermitAll;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for system status information.
 * Provides public endpoints for health checks and status information.
 */
@RestController
@PermitAll
@RequestMapping("/api/status")
public class StatusController {

    /**
     * Returns the current system status.
     * This endpoint is publicly accessible for health checks.
     *
     * @return A map containing status information
     */
    @GetMapping
    @PermitAll
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "UP");
        status.put("timestamp", System.currentTimeMillis());
        return status;
    }
}