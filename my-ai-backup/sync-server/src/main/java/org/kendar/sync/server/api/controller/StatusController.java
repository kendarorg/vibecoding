package org.kendar.sync.server.api.controller;

import jakarta.annotation.security.PermitAll;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for server status.
 */
@RestController
@PermitAll
@RequestMapping("/api/status")
public class StatusController {

    /**
     * Gets the server status.
     *
     * @return The server status
     */
    @GetMapping
    @PermitAll
    public ResponseEntity<String> getSettings() {

        return ResponseEntity.ok("OK");
    }
}