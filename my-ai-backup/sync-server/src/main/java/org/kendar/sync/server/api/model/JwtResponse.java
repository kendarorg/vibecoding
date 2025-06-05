package org.kendar.sync.server.api.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * Response model for JWT authentication.
 */
@SuppressWarnings("ClassCanBeRecord")
public class JwtResponse implements Serializable {
    @Serial
    private static final long serialVersionUID = -8091879091924046844L;

    private final String token;
    private final String username;
    private final String userId;
    private final boolean isAdmin;

    /**
     * Creates a new JWT response.
     *
     * @param token    The JWT token
     * @param username The username
     * @param userId   The user ID
     * @param isAdmin  Whether the user is an admin
     */
    public JwtResponse(String token, String username, String userId, boolean isAdmin) {
        this.token = token;
        this.username = username;
        this.userId = userId;
        this.isAdmin = isAdmin;
    }

    // Getters
    public String getToken() {
        return token;
    }

    public String getUsername() {
        return username;
    }

    public String getUserId() {
        return userId;
    }

    public boolean isAdmin() {
        return isAdmin;
    }
}