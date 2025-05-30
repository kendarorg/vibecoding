package org.kendar.sync.server.api.controller;

import jakarta.annotation.security.PermitAll;
import org.kendar.sync.lib.model.ServerSettings;
import org.kendar.sync.server.config.ServerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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