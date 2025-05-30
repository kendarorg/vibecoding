package org.kendar.sync.server.api.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * Response model for JWT authentication.
 */
public record JwtResponse(String token, String username, String userId, boolean isAdmin) implements Serializable {
    @Serial
    private static final long serialVersionUID = -8091879091924046844L;

    /**
     * Creates a new JWT response.
     *
     * @param token    The JWT token
     * @param username The username
     * @param userId   The user ID
     * @param isAdmin  Whether the user is an admin
     */
    public JwtResponse {
    }
}