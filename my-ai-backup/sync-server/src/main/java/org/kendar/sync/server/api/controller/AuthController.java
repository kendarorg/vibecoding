package org.kendar.sync.server.api.controller;

import org.kendar.sync.lib.model.ServerSettings;
import org.kendar.sync.server.api.model.JwtRequest;
import org.kendar.sync.server.api.model.JwtResponse;
import org.kendar.sync.server.security.JwtTokenUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Controller for authentication.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final JwtTokenUtil jwtTokenUtil;
    private final ServerSettings serverSettings;
    public AuthController(JwtTokenUtil jwtTokenUtil, ServerSettings serverSettings) {
        this.jwtTokenUtil = jwtTokenUtil;
        this.serverSettings = serverSettings;
    }

    /**
     * Authenticates a user and generates a JWT token.
     *
     * @param authenticationRequest The authentication request
     * @return The JWT token
     */
    @PostMapping("/login")
    public ResponseEntity<?> createAuthenticationToken(@RequestBody JwtRequest authenticationRequest) {
        // Authenticate the user
        Optional<ServerSettings.User> userOpt = serverSettings.authenticate(
                authenticationRequest.getUsername(), authenticationRequest.getPassword());

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body("Invalid username or password");
        }

        ServerSettings.User user = userOpt.get();

        // Generate token
        final String token = jwtTokenUtil.generateToken(user);

        return ResponseEntity.ok(new JwtResponse(token, user.getUsername(), user.getId(), user.isAdmin()));
    }
}